(ns clojbot.modules.seen
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojbot.commands  :as  cmd]
            [clojbot.botcore   :as core]
            [clojbot.utils     :as    u]
            [clj-time.coerce   :as    c]
            [clj-time.core     :as    t]
            [clj-time.format   :as    f]
            [clojbot.db        :as   db]))

(def url-regex #"(https?|ftp):\/\/[^\s/$.?#].[^\s]*")
(def shout "Oud! Eerst gepost door %s op %s (%s).")
(def timeformat (f/formatter "dd/MM/yyy HH:mm"))

;;;;;;;;;;;;;;;;;;;;;
;; Database Entity ;;
;;;;;;;;;;;;;;;;;;;;;

(defentity seen
  (database (db/read-db-config)))

;;;;;;;;;;;;;
;; Helpers ;;
;;;;;;;;;;;;;

(defn update-activity
  "Updates the last activity of the given username to this instant."
  [user channel]
  (let [now (java.util.Date.)]
    (if-not (first (select seen (where {:nick user :channel channel})))
      (insert seen 
              (values {:nick    user 
                       :time    now
                       :channel channel}))
      (update seen
              (set-fields {:time now})
              (where {:channel channel :nick user})))))

(defn last-activity
  "Retrieves the last activity for a given user in string format."
  [user channel]
  (let [result (select seen 
                       (where {:nick user :channel channel})
                       (order :time :DESC))
        record (first result)]
    (when record
      (c/from-long (:time record)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(core/defstorage
  :seen [:nick :text] [:time :timestamp] [:channel :text])


(core/defmodule
  :seen
  0
  (core/defcommand
    "seen"
    (fn [srv args msg]
      (if-let [last-seen (last-activity args (:channel msg))]
        (cmd/send-message srv (:channel msg) (format "Last activity of %s was around %s" args (f/unparse timeformat last-seen)))
        (cmd/send-message srv (:channel msg) "I have no data about that user. Ask the NSA for a more detailed activity log."))))
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      (let [sender (u/sender-nick msg)]
        (update-activity sender (:channel msg))))))
