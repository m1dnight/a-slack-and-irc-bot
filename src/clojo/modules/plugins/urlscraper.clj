(ns clojbot.modules.urlscraper
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojbot.commands  :as  cmd]
            [clojbot.botcore   :as core]
            [clojbot.utils     :as    u]
            [clj-time.coerce   :as    c]
            [clj-time.format   :as    f]
            [clojbot.db        :as   db]))

(def url-regex #"(https?|ftp):\/\/[^\s/$.?#].[^\s]*")
(def shout "Repost! First posted by  %s on %s UTC (%s).")
(def timeformat (f/formatter "dd/MM/yyy HH:mm z"))

;;;;;;;;;;;;;;;;;;;;;
;; Database Entity ;;
;;;;;;;;;;;;;;;;;;;;;

(defentity urls
  (database (db/read-db-config)))

;;;;;;;;;;;;;
;; Helpers ;;
;;;;;;;;;;;;;

(defn get-urls-from-line
  "Takes a line of text and returns all the urls present in the line. Filters
  out duplicates as well."
  [line]
  (distinct (map first (re-seq url-regex line))))


(defn repost?
  "Checks if the database contains the given url."
  [url channel]
  (first
   (select urls 
           (where {:url url :channel channel})
           (order :time :ASC))))


(defn insert-into-database
  "Inserts the given URL into the database."
  [sender url channel]
  (insert urls (values {:sender  sender 
                        :url     url 
                        :time    (java.util.Date.) 
                        :channel channel})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Module Implementation ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(core/defstorage
  :urls [:sender :text] [:url :integer] [:time :timestamp] [:channel :text])


(core/defmodule
  :urlscraper
  0
  (core/defhook
    :PRIVMSG
    (fn [srv msg]
      ;; Fetch all URL's in the message and insert them into the database.
      (let [urls   (get-urls-from-line (:message msg))
            chan   (:channel msg)  
            sender (u/sender-nick msg)]
        (doseq [url urls]
          (when-let [data (repost? url chan)]
            (let [sender (:sender data)
                  when   (f/unparse timeformat (c/from-long (:time data)))]
              (cmd/send-message srv chan (format shout sender when url))))
          (insert-into-database sender url chan))))))
