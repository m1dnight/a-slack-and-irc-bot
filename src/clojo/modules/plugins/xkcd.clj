(ns clojo.modules.plugins.xkcd
  (:require [clojo.modules.macros  :as    m]
            [clojo.utils           :as    utils]
            [clojure.algo.monads   :refer :all]
            [clojure.data.json     :as    json]
            [clojure.string          :as    string]
            [clojure.tools.logging :as    log]
            [clj-http.client       :as    client]))

(def xkcd-url "http://xkcd.com/")
(def xkcd-json "info.0.json")

(def slack-api "https://slack.com/api/")
(def post-message-method "chat.postMessage")

(defn- get-xkcd
  []
  (let [url (str xkcd-url xkcd-json)]
    (try (:body (client/get url))
         (catch Exception e
           (log/error "GET" url "failed" (.getMessage e))
           nil))))

(defn- message-attachments
  [xkcd]
  (domonad maybe-m
           [title (:title xkcd)
            img   (:img xkcd)
            alt   (:alt xkcd)
            num   (:num xkcd)]
           (let [url (str "http://www.xkcd.com/" num)]
             [{"fallback" (str title " - " url)
               "title" title
               "title_link" url
               "image_url" img
               "color" "#96A8C8"}
              {"fallback" alt
               "text" (str "```" alt "```")
               "mrkdwn_in" ["text"]}])))

(defn- post-attachments
  [instance channel attachments]
  (let [url (str slack-api post-message-method)]
    (try
      (let [raw-reply (:body (client/post url {:form-params {:token (:token @instance)
                                                      :channel channel
                                                      :text ""
                                                      :as_user true
                                                      :attachments (json/write-str attachments)}}))
            reply (utils/keywordize-keys (json/read-str raw-reply))]
        (when (false? (:ok reply))
          (log/error "Slack API reply not ok" (:error reply))))
      (catch Exception e
        (log/error "POST" url "failed" (.getMessage e))
        nil))))

(m/defmodule                                                                                   
  :xkcd
  2000
  (m/defcommand                                                                                
    "xkcd"                                                                                    
    (fn [instance args msg]
      (domonad maybe-m
               [json (get-xkcd)
                xkcd (utils/keywordize-keys (json/read-str json))
                attachments (message-attachments xkcd)]
               (post-attachments instance (:channel msg) attachments)))))
