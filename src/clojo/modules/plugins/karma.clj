(ns clojbot.modules.karma
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojbot.botcore  :as core]
            [clojbot.db       :as db  ]
            [clojbot.commands :as cmd ]))

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
  [username]
  (first (select karma (where {:nick username}))))


(defn track-user
  "Inserts a new user into the database with karma 0."
  [username]
  (insert karma (values {:nick username :karma 0})))


(defn update-karma
  "Updates the karma in the database with the given delta."
  [username delta]
  ;; Track the user if not yet tracked.
  (when-not (get-user-data username)
    (track-user username))

  (let [data    (get-user-data username)
        updated (+ (:karma data) delta)]
    (update karma 
            (set-fields {:karma updated})
            (where {:nick username}))))


(defn get-karma
  "Gets the karma value for the given username, nil if the user is not found."
  [username]
  (when-let [data (get-user-data username)]
    (:karma data)))

;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DEFINITION ;;
;;;;;;;;;;;;;;;;;;;;;;;

(core/defstorage
  :karma [:nick :text] [:karma :int] [:context :text])


(core/defmodule
  :karma
  0
  (core/defcommand
    "karma"
    (fn [srv args msg]
      (if-let [karma (get-karma args)]
        (cmd/send-message srv (:channel msg) (str "↪ " karma))
        (cmd/send-message srv (:channel msg) "I have no information about that user."))))
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      (let [incd (map second (re-seq #"(\w+)\+\+" (:message msg)))
            decd (map second (re-seq #"(\w+)\-\-" (:message msg)))]
        (doall (map #(update-karma % -1) decd))
        (doall (map #(update-karma % +1) incd))))))
