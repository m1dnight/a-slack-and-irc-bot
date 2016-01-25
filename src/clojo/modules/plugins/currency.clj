(ns clojbot.modules.currency
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

(def oe-url "http://openexchangerates.org/api/latest.json")

;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- get-currency-rates
  [url apikey]
  (try (:body (client/get url {:query-params {:app_id apikey}}))
       (catch Exception e
         (log/error "GET" url "failed" (.getMessage e))
         nil)))

(defn- raw-to-json
  [raw]
  (try (u/keywordize-keys (json/read-str raw))
       (catch Exception e
         (log/error "Failed to parse JSON! Given input:" raw)
         nil)))

(defn- get-currency
  [json currency-code]
  ((comp (keyword currency-code) :rates) json))


(defn- convert
  [from to amount]
  (domonad maybe-m
           [apikey     (:apikey (u/read-config "openexchange.edn"))
            currencies (get-currency-rates oe-url apikey)
            parsed     (raw-to-json currencies)
            from-curr  (get-currency parsed from)
            to-curr    (get-currency parsed to)]
           (do (println            (* (/ amount from-curr) to-curr))
               (* (/ amount from-curr) to-curr))))

(defn- parse-input
  [inputstring]
  (domonad maybe-m
           [upper (str/upper-case inputstring)
            prsd  (re-find #"([0-9]+(\.[0-9]+)?)\s(\w+)\s(TO)\s(\w+)" upper)
            value (read-string (nth prsd 1))
            from  (nth prsd 3)
            to    (nth prsd 5)]
           (do (println            {:val value :fr from :to to})
               {:val value :fr from :to to})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(core/defmodule                                                                                   
  :currency
  0                                                                                   
  (core/defcommand                                                                                
    "curr"                                                                                        
    (fn [srv args msg]
      (domonad maybe-m
               [prsd    (parse-input args)
                cvrted  (convert (:fr prsd) (:to prsd) (:val prsd))
                message (format "%.2f %s" cvrted (:to prsd))]
               (cmd/send-message srv (:channel msg) message)))))
