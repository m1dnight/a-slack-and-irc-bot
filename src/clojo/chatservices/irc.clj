                                        ; Author:  Christophe De Troyer
                                        ; Email:   christophe.detroyer@gmail.com
                                        ; License: GPLv3
                                        ; Date:    May 16, 2016


(ns clojo.chatservices.irc
  (:import  [java.net              Socket                                      ]
            [java.io               PrintWriter InputStreamReader BufferedReader]
            [java.util.concurrent  LinkedBlockingQueue TimeUnit                ]
            [java.lang             InterruptedException                        ])
  (:require [clojure.tools.logging :as  log]
            [clojure.core.async    :as   as]
            [clj-http.client       :as http]
            [gniazdo.core          :as   ws]
            [cheshire.core         :as   cs]
            [clojo.utils           :as    u]))


(declare from-config)
(declare connect)
(declare monitor-server)
(declare send-message)
(declare process-outgoing)
(declare registered?)
(declare write-message)
(declare register-user)
(declare handle-message)
(declare join-channels)
(declare change-nick)
(declare parse-command)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; PUBLIC FUNCTIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn create-instance
  "Creates an instance for a slack connection based on a config."
  [cfg-map dispatcher]
  (let [instance (from-config cfg-map)]
    (dosync
     (alter instance #(assoc % :dispatcher dispatcher)))
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
  (let [required [:type :name :modules :serverip :serverport]]
    (when-not (every? cfg required)
      (log/error "Cannot create connection with incomplete config:\n" cfg "\nrequired: " required))))


(defn- from-config
  "Given a config, created a transactional variable that will contain all the
  state of this bot instance."
  [cfg]
  (ref
   {
    :type        :irc
    :name        (:name cfg)
    :serverip    (:serverip cfg)
    :serverport  (:serverport cfg)
    :incoming    (as/chan)
    :outgoing    (as/chan)
    :heartbeat   (as/chan)
    :connected   false
    :socket      nil
    :id-gen      0
    :send-fn     send-message
    :ratelimits  {}
    :rcv-thread  nil
    :snd-thread  nil
    :registered  nil
    }))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Connection to IRC


(defn- create-socket
  "Creates a socket for the given ip and port. Returns nil if
  impossible."
  [instance]
  (try
    (let [ip       (get-in @instance [:serverip])
          port     (get-in @instance [:serverport])
          socket   (doto (Socket. ip port)
                     (.setSoTimeout 5000))
          in       (BufferedReader.
                    (InputStreamReader.
                     (.getInputStream socket)))
          out      (PrintWriter. (.getOutputStream socket))
          clean-fn #(do
                      (.close socket)
                      (.close in)
                      (.close out))]
      {:in in :out out :cleanup clean-fn})
    (catch java.net.SocketException e
      nil)
    (catch java.net.UnknownHostException e
      nil)))


(defn- connect-socket
  "Takes a server, attaches a socket to it and starts the read-in and
  -out loops."
  [instance]
  (when-let [socket (create-socket instance)]
    ;; Store the socket in the server instance. Note we are not yet
    ;; receiving anything, nor are we sending anything. We have just
    ;; established the connection.
    (dosync
     (alter instance
            #(assoc %
                    :socket socket
                    :connected true)))
    ;; After socket is in place and connected is set to true, start
    ;; loops to send and receive data.
    (let [receiver-thread
          (u/loop-thread-while
           (fn [] (process-outgoing instance))
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
                      :snd-thread sender-thread))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Reconnecting And Monitor

(defn- reconnect-server
  "Creates a new socket for this server instance, re-registers on the
  network and re-joins channels."
  [instance]
  (log/error (:name @instance) "Reconnecting")
  ;; First clean up current connections, if any.
  (when (:socket @instance)
    (-> @instance :socket :cleanup))
  ;; Reset status.
  (dosync
   (alter instance
          #(assoc %
                  :socket     nil
                  :connected  false
                  :registered false)))
  (connect-socket instance))

(defn- heartbeat
  "Send a ping message to the Slack instance. This function should be run in a
  seperate thread to be executing perpetually."
  [instance]
  ;; Send the ping message only if we are registered.
  (when (registered? instance)
    (write-message instance (str "PING clojo"))
    ;; Await for the reply on the heartbeat channel.
    ;; Only reconnect if we are not connected or registered.
    (if-let [reply (u/read-with-timeout (:heartbeat @instance) 5000)]
      ;; If a pong is received, wait for a proper amount of time.
      (do (log/trace (:name @instance) "Heartbeat - Received heartbeat")
          (Thread/sleep 5000))
      ;; If no pong is received, try a reconnect and keep trying until it
      ;; succeeds.
      (dosync
       (log/trace (:name @instance) "Heartbeat - Did not receive heartbeat, reconnecting.")
       (reconnect-server instance)
       (while (not (:connected @instance))
         (Thread/sleep 2000) ;; Attempt connect every 2 seconds.
         (reconnect-server instance))))))


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
;;; Irc-Specific Helpers


(defn- register-server
  "Registers the bot on the network."
  [instance]
  (register-user instance)
  instance)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Raw Socket Commands


(defn- write-out
  "Writes a raw line to the socket."
  [socket msg]
  (doto (:out socket)
    (.println (str msg "\r"))
    (.flush)))


(defn- read-in
  "Reads a line from the socket and parses it into a message
  map. Returns nil if there has been an exception reading from the
  socket or a timeout."
  [socket]
  (try
    (let [line (.readLine (:in socket))]
      (when line
        (log/trace "RAW IN :: " line)
        (let [parsed (u/destruct-raw-message line)]
          (log/trace "PARSED :: " parsed)
          parsed)))
    (catch Exception e
      nil)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket Comm Functions


(defn- process-incoming
  "Reads a message from the incoming socket and dispatches it."
  [instance]
  (let [msg (read-in (:socket @instance))]
    (when msg
      (handle-message msg instance))))


(defn- process-outgoing
  "Processes a message from the outbound channel and writes it to the socket."
  [instance]
  (when-let [msg (u/read-with-timeout (:out-chan @instance) 5000)]
    (log/trace (:name @instance) "OUT: " msg)
    (write-out (:socket @instance) msg)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Message Protocol


(defn- handle-change-nick
  "Actions to take when a nickchange has been announced for the current instance."
  [instance nickname]
  (log/info "### ::" "Changed nick to " nickname)
  (dosync
   (alter instance #(update-in % [:info :nick] (fn [_] nickname)))
   (log/trace "Nickname changed to " nickname)))


(defn- handle-ping
  "Actions to take when the server sends us a ping message."
  [instance ping]
  (write-message instance (str "PONG " (re-find #":.*" ping))))


(defn- handle-001
  "Actions to take when the server sends the 001 command. 001 means
  that we have been registered on the server."
  [instance]
  (log/info (:name @instance) "Registered")
  (dosync
   (alter instance #(assoc % :registered true)))
  ;; Join the channels
  (join-channels instance))


(defn- handle-error
  "Actions to take when the server sends an ERROR command. Probably
  means disconnecting."
  [instance]
  (log/info "### ::" "Server is closing link. Exiting bot.")
  (dosync
   (alter instance
          #(assoc %
                  :registered false
                  :connected  false))))


(defn- handle-pong
  "Actions to take when the server sends us a pong message. Pong is a
  reply to our ping."
  [instance msg]
  (let [pong-id (re-find #":.*" (:original msg))]
    (as/>!! (:hb-chan @instance) (:original msg))))


(defn- handle-nick-taken
  "Actions to take when the server lets us know that our nickname has already
  been taken."
  [instance]
  ;; Swap the nicklist in the bot by shifting it one position.
  (dosync
   (alter
    instance #(update-in % [:info :altnicks] u/shift-left)))
  (let [next (first (-> @instance :info :altnicks))]
    (change-nick instance next)))




(defn- handle-message
  "Function that dispatches over the type of message we receive."
  [msg instance]
  ;; Ignored messages.
  (when-not (contains? #{"372"} (:command msg))
    (log/trace (:name @instance) " IN " (:original msg)))
  (cond
    (= "NICK" (:command msg))
    (handle-change-nick instance (:message msg))
    (re-find #"^PING" (:original msg))
    (handle-ping instance (:original msg))
    (= "001" (:command msg))
    (handle-001 instance)
    (re-find #"^ERROR :Closing Link:" (:original msg))
    (handle-error instance)
    (= "PONG"  (:command msg))
    (handle-pong instance msg)
    (= "433" (:command msg))
    (handle-nick-taken instance)
    :else
    ((:dispatcher @instance) instance msg)))
