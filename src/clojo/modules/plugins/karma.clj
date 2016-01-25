(ns clojo.modules.plugins.karma
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojo.modules.macros  :as   m]
            [clojo.modules.modules :as mod]
            [clojo.db              :as  db]))

;;;;;;;;;;;;;;;;;;;;;
;; Database Entity ;;
;;;;;;;;;;;;;;;;;;;;;

(defentity karma
  (database (db/read-db-config)))

;;;;;;;;;;;;;
;; HELPERS ;;
;;;;;;;;;;;;;

(defn get-user-data
  "Returns the entry for a user if it is being tracked. Nil otherwise."
  [username channel]
  (first (select karma (where {:nick username :context channel}))))


(defn track-user
  "Inserts a new user into the database with karma 0."
  [username channel]
  (insert karma (values {:nick username :karma 0 :context channel})))


(defn update-karma
  "Updates the karma in the database with the given delta."
  [username delta channel]
  ;; Track the user if not yet tracked.
  (when-not (get-user-data username channel)
    (track-user username channel))

  (let [data    (get-user-data username channel)
        updated (+ (:karma data) delta)]
    (update karma 
            (set-fields {:karma updated})
            (where {:nick username :context channel}))))


(defn get-karma
  "Gets the karma value for the given username, nil if the user is not found."
  [username channel]
  (when-let [data (get-user-data username channel)]
    (:karma data)))

;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DEFINITION ;;
;;;;;;;;;;;;;;;;;;;;;;;

(m/defstorage
  :karma [:nick :text] [:karma :int] [:context :text])


(m/defmodule
  :karma
  0
  (m/defcommand
    "karma"
    (fn [instance args msg]
      (if-let [karma (get-karma args (:channel msg))]
        (m/reply instance msg (str "↪ " karma))
        (m/reply instance msg "I have no information about that user."))))
  (m/defhook
    :PRIVMSG
    (fn [instance msg]
      (let [incd (map second (re-seq #"(\w+)\+\+" (:message msg)))
            decd (map second (re-seq #"(\w+)\-\-" (:message msg)))]
        (doall (map #(update-karma % -1 (:channel msg)) decd))
        (doall (map #(update-karma % +1 (:channel msg)) incd))))))
