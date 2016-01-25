(ns clojbot.modules.catfacts
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


(core/defmodule                                                                                   
  :catfacts
  10000
  (core/defcommand                                                                                
    "catfact"                                                                                        
    (fn [srv args msg]
      (domonad maybe-m
               [json (get-catfact catfacturl)
                fact (extract-catfact json)]
               (cmd/send-message srv (:channel msg) fact)))))
