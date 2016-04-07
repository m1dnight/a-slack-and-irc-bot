(ns clojo.modules.plugins.uwmoeder
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojo.modules.macros  :as   m]
            [clojo.modules.modules :as mod]
            [clojo.utils           :as   u]))


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
(m/defmodule
  :insult
                                        ;7200000 ;; every 3 hours tops
  0
  (m/defhook
    :PRIVMSG
    (fn [instance msg]
      (when-let [insult (change-to-insult (:message msg))]
        (when (u/rand-bool)
          (m/reply instance msg insult))))))
