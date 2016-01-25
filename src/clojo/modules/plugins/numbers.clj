(ns clojbot.modules.numbers
  (:use [clojure.algo.monads])
  (:require[clj-http.client       :as client]
           [clojure.tools.logging :as log   ]
           [clojure.data.json     :as json  ]
           [clojure.edn           :as edn   ]
           [clojbot.commands      :as cmd   ]
           [clojbot.botcore       :as core  ]
           [clojbot.utils         :as u     ]
           [clojure.string        :as str   ]
           [clojure.java.io       :as io    ]))

;;;;;;;;;;;;;;;;;;;;
;; URLS AND STUFF ;;
;;;;;;;;;;;;;;;;;;;;

(def numbersurl "http://numbersapi.com/%s")

;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- extract-random-number
  [msg]
  (rand-nth (re-seq #"\d+" msg)))

(defn- number-fact
  [url number]
  (try (:body (client/get (format url number )))
       (catch Exception e
         (log/error "GET" url "failed" (.getMessage e))
         nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(core/defmodule                                                                                   
  :numberfacts
  7200000                                                                                    
  (core/defcommand                                                                              
    "number"                                                                                        
    (fn [srv args msg]
      (domonad maybe-m
               [number (extract-random-number args)
                fact   (number-fact numbersurl number)]
               (cmd/send-message srv (:channel msg) fact)))))
