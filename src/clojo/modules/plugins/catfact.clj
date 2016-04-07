
(ns clojo.modules.plugins.catfact
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojo.modules.macros  :as      m]
            [clojo.modules.modules :as    mod]
            [clojo.utils           :as      u]
            [clojo.db              :as     db]
            [clj-http.client       :as client]
            [clojure.tools.logging :as    log]
            [clj-http.client       :as client]
            [clojure.data.json     :as   json]))

;;;;;;;;;;;;;;;;;;;;
;; URLS AND STUFF ;;
;;;;;;;;;;;;;;;;;;;;

(def catfacturl "http://catfacts-api.appspot.com/api/facts")

;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- get-catfact
  [url]
  (try (:body (client/get url))
       (catch Exception e
         (log/error "GET" url "failed" (.getMessage e))
         nil)))

(defn- extract-catfact
  [raw]
  (try (let [map (u/keywordize-keys (json/read-str raw))
             [f] (:facts map)]
         f)
       (catch Exception e
         (log/error "Failed to parse JSON! Given input:" raw)
         nil)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;


(m/defmodule                                                                                   
  :catfacts
  1000
  (m/defcommand                                                                                
    "catfact"                                                                                        
    (fn [instance args msg]
      (domonad maybe-m
               [json (get-catfact catfacturl)
                fact (extract-catfact json)]
               (m/reply instance msg fact)))))
