; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.rss
  (:use [clojure.pprint :only (pprint)])
  (:require [feedparser-clj.core :as rss]
            [overtone.at-at :as atat]))

(defn get-feed
  "Gets the RSS feed."
  [link]
  (try
    (let [content  (rss/parse-feed link)]
      content)
    (catch Exception e
      (println "Error getting RSS feed content: " link))))


(defn feed-entries
  "Gets the entries for a given RSS feed."
  [link]
  (let [feed (get-feed link)
        entries (:entries feed)]
    entries))
