(ns clojbot.modules.shorten
  (:use     [clojure.algo.monads              ]
            [hobbit.bitly                     ]
            [hobbit.core                      ])
  (:require [clojbot.botcore          :as core]
            [clojbot.commands         :as cmd ]
            [clojbot.utils            :as u   ]
            [clojure.tools.html-utils :as ut  ]))

;;;;;;;;;;;;;
;; HELPERS ;;
;;;;;;;;;;;;;

(defn tiny-fy
  [url]
  (let [config (u/read-config "shorten.edn")
        limit  (:limit config)
        auth   (:auth config)]
    (when (> (count url) limit)
      (shorten (shortener :bitly auth) url))))


;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DEFINITION ;;
;;;;;;;;;;;;;;;;;;;;;;;


(core/defmodule
  :urlshortener
  0
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      (let [urls (u/get-urls-from-line (:message msg))]
        (doseq [url urls]
          (domonad maybe-m
                   [tinyfied (tiny-fy url)]
                   (cmd/send-message srv (:channel msg) tinyfied)))))))
