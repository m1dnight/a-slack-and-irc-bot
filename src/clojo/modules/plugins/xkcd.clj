(ns clojo.modules.plugins.xkcd
  (:require [clojo.modules.macros  :as    m]
            [clojo.utils           :as    utils]
            [clojure.algo.monads   :refer :all]
            [clojure.data.json     :as    json]
            [clojure.string        :as    string]
            [clojure.tools.logging :as    log]
            [clj-http.client       :as    client]))

(def xkcd-url "http://xkcd.com/")
(def xkcd-json "info.0.json")

(def slack-api "https://slack.com/api/")
(def post-message-method "chat.postMessage")

(defn- parse-args
  [args]
  {:requested-num (when args (or (if-let [match (or (re-find #"\b(\d+)\s+(?:back|ago)\b" args) (re-find #"\B-(\d+)\b" args))] (- (read-string (nth match 1))))
                                 (if-let [match (re-find #"\b(\d+)\b" args)] (read-string (nth match 1)))))})

(defn- get-json-from-url
  ([url]
   (get-json-from-url url client/get))
  ([url method]
   (domonad maybe-m
            [raw (try (:body (method url))
                      (catch Exception e
                      (log/error "HTTP request" url "failed" (.getMessage e))
                       nil))
             json (try (utils/keywordize-keys (json/read-str raw))
                       (catch Exception e
                         (log/error "Failed to parse JSON! Given input:" raw)
                         nil))]
             json)))

(defn- get-xkcd
  ([]
   (get-json-from-url (str xkcd-url xkcd-json)))
  ([num]
   (get-json-from-url (str xkcd-url num "/" xkcd-json))))

(defn- absolute-num
  [num]
  (let [max-num (:num (get-xkcd))]
    (if (> num 0)
      (if (<= num max-num)
        num)
      (if (< (- num) max-num)
        (+ max-num num)))))

(defn- message-attachments
  [xkcd]
  (domonad maybe-m
           [title (:title xkcd)
            img   (:img xkcd)
            alt   (:alt xkcd)
            num   (:num xkcd)]
           (let [url (str xkcd-url num "/")]
             [{"fallback" (str title " - " url)
               "title" (str title)
               "title_link" url
               "image_url" img
               "color" "#96A8C8"}
              {"fallback" alt
               "text" (str "```" alt "```")
               "mrkdwn_in" ["text"]}])))

(defn- post-attachments
  [instance channel attachments]
  (let [url (str slack-api post-message-method)
        reply (get-json-from-url url #(client/post % {:form-params {:token (:token @instance)
                                                                    :channel channel
                                                                    :text ""
                                                                    :as_user true
                                                                    :attachments (json/write-str attachments)}}))]
    (when (false? (:ok reply))
      (log/error "Slack API reply not ok" (:error reply)))))

(m/defmodule                                                                                   
  :xkcd
  2000
  (m/defcommand                                                                                
    "xkcd"                                                                                    
    (fn [instance args msg]
      (domonad maybe-m
               [parsed-args (parse-args args)
                xkcd (if-let [requested-num (:requested-num parsed-args)]
                       (if-let [abs-num (absolute-num requested-num)]
                         (get-xkcd abs-num)
                         (m/reply instance msg "That's not valid xkcd comic, dummy!"))
                       (get-xkcd))
                attachments (message-attachments xkcd)]
               (post-attachments instance (:channel msg) attachments)))))
