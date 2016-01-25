(ns clojbot.modules.uwmoeder
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojbot.botcore  :as core]
            [clojbot.db       :as db  ]
            [clojbot.commands :as cmd ]
            [clojbot.utils    :as u   ]))


(defn change-to-insult
  [message]
  ;; I know (second (rest x)) is ugly but we are sure that either nil or (x y z)
  ;; comes out of the regex matcher.
  (when-let [parsed (re-matches #"([\w ]+)is(\s[\w ]+)" message)]
    (let [suffix (second (rest parsed))]
      (str "Uw moeder is" suffix "."))))



;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DEFINITION ;;
;;;;;;;;;;;;;;;;;;;;;;;
(core/defmodule
  :insult
  14400000 ;; every 6 hours tops
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      (when-let [insult (change-to-insult (:message msg))]
        (when (u/rand-bool)
          (cmd/send-message srv (:channel msg) insult))))))
