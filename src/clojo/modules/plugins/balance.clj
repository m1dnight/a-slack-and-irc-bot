(ns clojo.modules.plugins.balance
  (:use     [clojure.algo.monads ]
            [korma.core          ]
            [korma.db            ])
  (:require [clojo.modules.macros  :as   m]
            [clojo.modules.modules :as mod]
            [clojo.db              :as  db]
            [clojo.utils           :as   u]
            [clojure.tools.logging :as log]
            [clojure.string        :as   s]))

;;;;;;;;;;;;;;;;;;;;;
;; Database Entity ;;
;;;;;;;;;;;;;;;;;;;;;

(defentity balance
  (database (db/read-db-config)))

;;;;;;;;;;;;;
;; Helpers ;;
;;;;;;;;;;;;;

(defn char-filter
  [char]
  (fn [sequence] (count (filter #{char} sequence))))

(defn count-parens
  [string]
  (reduce (fn [m ch]
            (cond (= \( ch)
                  (update-in m [:lpar] + 1)
                  (= \) ch)
                  (update-in m [:rpar] + 1)
                  (= \[ ch)
                  (update-in m [:lbrack] + 1)
                  (= \] ch)
                  (update-in m [:rbrack] + 1)
                  :else
                  m))
          {:lpar 0 :rpar 0 :rbrack 0 :lbrack 0} string))


(defn build-string
  [kind value]
  (cond (= 0 value)
        ""
        (u/positive? value)
        (format "%s right %s" value kind)
        (u/negative? value)
        (format "%s left %s" (Math/abs value) kind)))

;;;;;;;;;;;;;;
;; Database ;;
;;;;;;;;;;;;;;

(defn get-kind-data
  "Returns the entry for a user if it is being tracked. Nil otherwise."
  [kind channel]
  (first (select balance (where {:kind kind :channel channel}))))


(defn track-kind
  "Inserts a new user into the database with karma 0."
  [kind channel]
  (insert balance (values {:kind kind :balance 0 :channel channel})))


(defn update-kind
  "Updates the karma in the database with the given delta."
  [kind delta channel]
  ;; Track the user if not yet tracked.
  (when-not (get-kind-data kind channel)
    (track-kind kind channel))

  (let [data    (get-kind-data kind channel)
        updated (+ (:balance data) delta)]
    (update balance 
            (set-fields {:balance updated})
            (where {:kind kind :channel channel}))))


(defn get-kind-balance
  [kind channel]
  (if-let [data (get-kind-data kind channel)]
    (:balance data)
    0))



;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(m/defstorage
  :balance [:kind :text] [:balance :integer] [:channel :text])


(m/defmodule
  :balance
  0
  (m/defcommand
    "balance"
    (fn [instance args msg]
      (let [parens   (get-kind-balance "parentheses" (:channel msg))
            bracks   (get-kind-balance "brackets"    (:channel msg))
            parens-m (build-string "parentheses" parens)
            bracks-m (build-string "brackets"    bracks)
            m (if (= 0 (apply + [parens bracks]))
                      "The universe is balanced!"
                      (format "The universe needs: %s" (s/join ", " (filter not-empty [parens-m bracks-m]))))]
        (m/reply instance msg m))))
  (m/defhook
    :PRIVMSG
    (fn [srv msg]
      (let [counts (count-parens (:message msg))
            parens (- (:lpar counts)   (:rpar counts))
            bracks (- (:lbrack counts) (:rbrack counts))]
        (update-kind "parentheses" parens (:channel msg))
        (update-kind "brackets" bracks (:channel msg))))))
