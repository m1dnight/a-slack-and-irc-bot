; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.modules.plugins.vubresto
  (:require [clojo.utils           :as      u]
            [clojo.modules.macros  :as      m]
            [clojure.string        :as    str]
            [clojure.tools.logging :as    log] 
            [clj-http.client       :as client]
            [clojure.data.json     :as   json]
            [clojure.core.reducers :as      r]
            [clj-time.format       :as      f]
            [clj-time.core         :as      t]
            [clj-time.local        :as      l]))

;;TODO Do this with monads!!
;;;;;;;;;;;;;
;; HELPERS ;;
;;;;;;;;;;;;;

(def ^:dynamic resto-url-nl "https://call-cc.be/files/vub-resto/etterbeek.nl.json")
(def ^:dynamic resto-url-en "https://call-cc.be/files/vub-resto/etterbeek.en.json")


(defn parse-args
  "Parses the arguments of the command into a map with default values."
  [argstring]
  (let [parts (if argstring (map keyword (str/split argstring #" ")) [])]
    {:language (or (some #{:en :nl} parts) :nl) ;default is dutch
     :when     (or (some #{:vandaag :morgen :overmorgen} parts) :vandaag)})) ; default is today


(defn build-date 
  [{when :when}]
  (t/plus 
   (l/local-now) 
   (t/days (cond (= :morgen when)
                 1
                 (= :overmorgen when)
                 2
                 :else
                 0))))


(defn- get-page
  "Requests a page source and returns error if it failed."
  [url & args]
  (try (:body (apply client/get url args))
       (catch Exception e
         (log/error "GET" url "failed!\n" (.getMessage e))
         {:error "Error making request. Check logs."})))

(defn get-resto-json
  "Requests the json data and returns it parsed into clojure
  datastructures."
  ;;; Default to dutch
  ([{lang :language}]
   (let [response (get-page (var-get (ns-resolve 'clojo.modules.plugins.vubresto (symbol (str "resto-url-" (name lang))))))]
     (when (not (:error response))
       (u/keywordize-keys (json/read-str response))))))


(defn find-day
  "Takes the parsed json data and returns the map that holds the
  restaurant data for today."
  [jsn date]
  (let [daystring (f/unparse  (f/formatter "yyy-MM-dd") date)]
    ;; Return first because we expect only a single result.
    (first (filter  #(= (:date %) daystring) jsn))))


(defn menu-to-string
  [menu-array]
  (str/join "  ~  "  (map #(:dish %) (:menus menu-array))))


;;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DECLARATION ;;
;;;;;;;;;;;;;;;;;;;;;;;;

(m/defmodule
  :vubresto
  0
  (m/defcommand
    "fret"
    (fn [instance args msg]
      (let [parsed (parse-args args)
            res (find-day (get-resto-json parsed) (build-date parsed))]
        (if res
          (m/reply instance msg (menu-to-string res))
          (m/reply instance msg "Error getting data. Go go gadget debugger!"))))))
