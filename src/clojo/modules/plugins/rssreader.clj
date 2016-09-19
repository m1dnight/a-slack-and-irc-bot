; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.modules.plugins.rssreader
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojure.tools.logging :as    log]
            [clojo.db              :as     db]
            [clojo.modules.macros  :as      m]
            [clojo.rss             :as    rss]))


(defentity rss
  (database (db/read-db-config)))


;;;;;;;;;;;;;
;; Storage ;;
;;;;;;;;;;;;;


(defn last-entry
  "Retrieves the hash of the last seen entry."
  [instance feed]
  (:last
   (first
    (select rss
            (where {:feedname feed
                    :inst     (str (:name @instance))})))))


(defn remember-entry
  "Takes a feed and an entry and stores the hash of that entry in the
  database."
  [instance feed entry]
  (update rss
          (set-fields {:last (str (hash entry))})
          (where {:feedname feed
                  :inst     (str (:name @instance))})))


(defn is-last?
  "Takes a feed and an entry and checks if that is the last seen entry."
  [instance feed entry]
  (let [last-seen (last-entry instance feed)]
    (= (str (hash entry)) last-seen)))


(defn to-show
  [instance feed]
  (let [newest    (first (rss/feed-entries feed))]
    (when-not (is-last? instance feed newest)
      (remember-entry instance feed newest)
      newest)))


;;;;;;;;;;;;;;;;;;;;;;;
;; MODULE DEFINITION ;;
;;;;;;;;;;;;;;;;;;;;;;;

;; We hash an entry and then store it in the database.
(m/defstorage
  :rss [:feedname :text] [:last :text] [:inst :text])


(defn rss-urls
  "Reads all the RSS feeds from the instance configuration."
  [instance]
  (let [feeds (get-in @instance [:cfg :rss-feeds])]
    feeds))


(m/defmodule
  :rss-reader
  0
  (m/defschedule
    :minutely
    (fn [instance]
      (println "cronjob")
      (let [rss-feeds (rss-urls instance)
            news      (doall (map #(to-show instance %) rss-feeds))]
        (doseq [entry news]
          (when entry
            (let [formatter #(format "%s - %s" (:title %) (:link %))]
              (m/broadcast instance (formatter entry)))))))))
