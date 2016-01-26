;; Author : Christophe De Troyer
;; Contact: <christophe.detroyer@gmail.com>
;; Date   : 20.01.2016
;; License: MIT
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;  ____  _            _      ____ _____ __  __  ;;
;; / ___|| | __ _  ___| | __ |  _ \_   _|  \/  | ;;
;; \___ \| |/ _` |/ __| |/ / | |_) || | | |\/| | ;;
;;  ___) | | (_| | (__|   <  |  _ < | | | |  | | ;;
;; |____/|_|\__,_|\___|_|\_\ |_| \_\|_| |_|  |_| ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(ns clojo.chatservices.slackrtm
  (:require [clojure.tools.logging :as  log]
            [clojure.core.async    :as   as]
            [clj-http.client       :as http]
            [gniazdo.core          :as   ws]
            [cheshire.core         :as   cs]
            [clojo.utils           :as    u]))


(declare from-config)
(declare process-incoming)
(declare process-outgoing)
(declare text-msg?)
(declare pong?)
(declare connect)
(declare decode-message)
(declare send-message)
(declare encode-message)
(declare monitor-server)
(declare send-message-raw)

(def ^:private socket-url "https://slack.com/api/rtm.start")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; PUBLIC FUNCTIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-instance
  "Creates an instance for a slack connection based on a config."
  [cfg-map dispatcher]
  (let [instance (from-config cfg-map)]
    (dosync (alter instance #(assoc % :dispatcher dispatcher)))
    instance))


(defn init-connection
  "Takes a bot instance and makes the actual connection to the Slack instance."
  [instance]
  (connect instance)
  (monitor-server instance)
  instance)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; PRIVATE FUNCTIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- verify-config
  "Given a config, verifies that all the required fields are present."
  [cfg]
  (let [required [:type :name :token :prefix]]
    (when-not (every? cfg required)
      (log/error "Cannot create connection with incomplete config:\n" cfg "\nrequired: " required))))


(defn- from-config
  "Given a config, created a transactional variable that will contain all the
  state of this bot instance."
  [cfg]
  (ref
   {
    :type        :slack-rtm
    :name        (:name cfg)
    :token       (:token cfg)
    :incoming    (as/chan)
    :outgoing    (as/chan)
    :heartbeat   (as/chan)
    :connected   false
    :socket      nil
    :id-gen      0
    :send-fn     send-message
    :ratelimits  {}
    }))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Connection to Slack


(defn- get-websocket-url 
  "Connects to Slack and request a websocket url, based on a given token.."
  [instance]
  (try
    (let [response (-> (http/get socket-url
                                 {:query-params {:token      (:token @instance)
                                                 :no_unreads true}
                                  :as :json})
                       :body)]
      (if (:ok response)
        (:url response)
        (log/error  (:name @instance) "Websocket URL refused by Slack! Error:" (:error response))))
    (catch Exception e
      (log/error  (:name @instance) "Could not get websocket URL from Slack instance.")
      nil)))


(defn- connect-websocket
  "Given an instance, connects to the defined Slack instance."
  [instance]
  (when-let [socket-url (get-websocket-url instance)]
    (let [rcv-fn     (fn [in]
                       (as/put! (:incoming @instance) in))
          error-fn   (fn [& arg]
                       (log/error (:name @instance) "Slack websocket error: " arg)
                       (as/close! (:incoming @instance))
                       (dosync (alter instance #(assoc % :connected false))))
          close-fn   (fn [& arg]
                       (log/error "Slack websocket closed." arg))
          socket     (ws/connect 
                      socket-url 
                      :on-receive rcv-fn
                      :on-error   error-fn
                      :on-close   close-fn)]
      (dosync 
       (alter instance #(assoc % :socket socket :connected true))))))


(defn- disconnect-websocket
  "Given an isntance, disconnects the websocket if possible."
  [instance]
  (dosync
   (let [socket (:socket @instance)]
     (try
       (ws/close socket)
       ;; If the socket is already closed, we don't care.
       (catch Exception e nil)))))


(defn- connect
  "Given an instance of a connection, makes the actual connection and listens
  for in- and outgoing messages on the channels."
  [instance]
  ;; First of all, connect the Slack socket.
  (connect-websocket instance)
  ;; Now we have a connection, set up the threads to listen for a message.
  (let [receiver-thread
        (u/loop-thread-while
         (fn [] (process-incoming instance))
         (fn [] (:connected @instance))
         (str (:name @instance) " - receiver thread"))
        sender-thread
        (u/loop-thread-while
         (fn [] (process-outgoing instance))
         (fn [] (:connected @instance))
         (str (:name @instance) " - sender thread"))]
    (dosync
     (alter instance
            #(assoc %
                    :rcv-thread receiver-thread
                    :snd-thread sender-thread)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Reconnecting And Monitor


(defn- reconnect-server
  "Reconnects the current server."
  [instance]
  (log/error (:name @instance) "Reconnecting")
  ;; Reset status.
  (dosync
   (disconnect-websocket instance)
   (alter instance
          #(assoc %
                  :socket     nil
                  :connected  false
                  :incoming   (as/chan))))
  (connect instance))


(defn- heartbeat
  "Send a ping message to the Slack instance. This function should be run in a
  seperate thread to be executing perpetually."
  [instance]
  ;; Send an initial ping.
  (send-message-raw instance {:id 1234 :type "ping"})
  ;; Wait for the reply.

  (if-let [reply (u/read-with-timeout (:heartbeat @instance) 5000)]
    ;; If a pong is received, wait for a proper amount of time.
    (Thread/sleep 5000)
    ;; If no pong is received, try a reconnect and keep trying until it
    ;; succeeds.
    (dosync
     (reconnect-server instance)
     (while (not (:connected @instance))
       (Thread/sleep 2000) ;; Attempt connect every 2 seconds.
       (reconnect-server instance)))))


(defn monitor-server
  "Given an instance, will attach a heartbeat thread to it. "
  [instance]
  (let [heartbeat (u/loop-thread-while
                   (fn [] (heartbeat instance))
                   (fn [] (:connected @instance))
                   (str (:name @instance) " - heartbeat thread"))]
    (dosync
     (alter instance
            #(assoc % :monitor heartbeat)))
    instance))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Message Handlers


(defn process-incoming
  "Tries to read a message from the channel. Times out after 5 seconds. When it
  has been read it dispatches the message to the dispatcher. If any
  Slack-specific messages have to be processed this is the place where it has to
  be done."
  [instance]
  (when-let [msg (cs/parse-string
                  (u/read-with-timeout (:incoming @instance) 5000) true)]
    (log/trace (:name @instance) "IN :" msg)
    (cond
      (pong? msg)
      (as/>!! (:heartbeat @instance) msg)
      ;; Passing to generic dispatch means translating the message to a generic format!
      (text-msg? msg)
      (let [parsed (decode-message msg)]
        (log/trace (:name @instance) "Dispatching:" parsed)
        ((:dispatcher @instance) instance parsed)))))


(defn process-outgoing
  "Reads a single message from the outgoing channel and sends it over the socket
  to the slack server."
  [instance]
  (when-let [msg (u/read-with-timeout (:outgoing @instance) 5000)]
    (log/trace "Putting message on socket:"  (cs/generate-string msg))
    (ws/send-msg (:socket @instance) (cs/generate-string msg))))


(defn send-message-raw
  "Sends a message over the socket without encoding it to the slack format."
  [instance map]
  (log/trace "Sending raw message" map)
  (let [chan (:outgoing @instance)]
    (as/>!! chan map)))


(defn send-message
  "Takes a generic message map. I.e., a map with the elements :message
  and :channel. Parses it and then sends it on its way to the infinite world of
  Slack! You will lose all control over your message. Once it is gone it is
  really gone."
  [instance channel message]
  (println "Sending message" message)
  (send-message-raw instance
   (encode-message instance {:message message :channel channel})))


(defn- handle-pong
  "Actions to take when the server sends us a pong message. Pong is a
  reply to our ping."
  [instance msg]
  (let [pong-id (:reply_to msg)]
    (as/>!! (:heartbeat @instance) msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; HELPER FUNCTIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn next-id
  "Given a bot instance, increments the message id by one and returns the new
  value."
  [instance]
  (dosync
   (let [old (:id-gen  @instance)
         new (inc old)]
     ;; Update id.
     (alter instance #(assoc % :id-gen new))
     old)))


(defn pong?
  "Check if an incoming message is a pong."
  [msg]
  (= "pong" (:type msg)))


(defn text-msg?
  "Given a raw input map from the websocket, checks if this map is a relevant
  Slack message. (I.e., not a status change or similar)."
  [msg]
  (and (= "message" (:type msg))
       (contains? msg :text)))


(defn clean-slack-msg
  [msg]
  (let [quotes (clojure.string/replace msg #"[\u201c\u201d]" "\"")
        newlines (clojure.string/replace quotes #"\n" " ")]
     newlines))


(defn decode-message
  "Given a Slack message, returns a hashmap with some uniform names."
  [m]
  {:message (clean-slack-msg (:text m))
   :channel (:channel m)
   :userid  (:user m)
   :nick    (:user m)
   :server  (:team m)
   :command :PRIVMSG ;; this is needed to trigger handlers for all public messages.
   })


(defn encode-message
  "Given a generic message map, turns it into a proper Slack message."
  [instance msg-map]
  (log/trace "Encoding" msg-map)
  (let [id (next-id instance)]
    (log/trace "Next message id" id)
    {
     :id      id
     :type    "message"
     :channel (:channel msg-map)
     :text    (:message msg-map)
     }))
