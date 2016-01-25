(ns clojbot.modules.or
  (:use [clojure.algo.monads])
  (:require [clojbot.commands :as cmd ]
            [clojbot.botcore  :as core]
            [clojure.string   :as str ]))

;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;

(defn find-options
  "Given a string that is seperated by ' or ' returns the options. Returns nil
  if there is only one option."
  [query]
  (let [options (filter #(not (str/blank? %)) (str/split query #" or "))]
    (when (> (count options) 1)
      options)))

(defn pick-option
  [options]
  (nth options (rand-int (count options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(core/defmodule
  :ormodule
  0
  (core/defcommand
    "or"
    (fn [srv args msg]
      (domonad maybe-m
               [options (find-options args)
                pick    (pick-option options)]
               (cmd/send-message srv (:channel msg) (format "I'd go for %s" pick))))))
