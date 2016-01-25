(ns clojo.modules.plugins.google
  (:require [clojure.tools.logging :as    log]
            [cemerick.url          :as    url]
            [clj-http.client       :as client]
            [clojo.modules.macros  :as      m]))	

;;;;;;;;;;;;;;;;;;;;;;;;;
;; HEADER FOR REQUESTS ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic header {"User-Agent"      "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
                       "Accept"          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                       "Accept-Encoding" "gzip, deflate"
                       "Accept-Language" "en-US,en;q=0.5"
                       "Connection"      "keep-alive"})

;;;;;;;;;;;;;;;;;;;;;;
;; HELPER FUNCTIONS ;;
;;;;;;;;;;;;;;;;;;;;;;


(defn- search
  "Gets the first search result form google, given a search term in
  query."
  [query]
  (let [url "http://www.google.com/search?q="
        charset "UTF-8"
        useragent "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
        search (str url (url/url-encode query))]
    ;; Get the source of the searchquery result.
    (let [resp    (client/get search {:headers header})
          matches (re-find #"<h3.*?><a href=\"(.+?)\" .*?>(.*?)<\/a>" (:body resp))]
      (when matches
        (let [reslink (url/url-decode (nth matches 1))
              resname (nth matches 2)]
          {:url reslink :title resname})))))


;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DEFINITION ;;
;;;;;;;;;;;;;;;;;;;;;;;


(m/defmodule
  :google
  0
  (m/defcommand
    "g"
    (fn [instance args msg]
      (log/debug "Searching Google for " args)
      (let [results (search args)]
        (println "results:" results)
        (println "msg:" msg)
        (if results
          (m/reply instance msg (str (:title results) " :: " (:url results)))
          (m/reply instance msg "No results found!"))))))
