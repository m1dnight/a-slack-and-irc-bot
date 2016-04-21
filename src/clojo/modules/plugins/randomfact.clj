(ns clojo.modules.plugins.randomfact
  (:use     [clojure.algo.monads])
  (:require [clojure.tools.logging :as    log]
            [cemerick.url          :as    url]
            [clj-http.client       :as client]
            [clojo.modules.macros  :as      m]
            [jsoup.soup            :as     js]))


(def header {"User-Agent"      "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
             "Accept"          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
             "Accept-Encoding" "gzip, deflate"
             "Accept-Language" "en-US,en;q=0.5"
             "Connection"      "keep-alive"})

(def url "http://www.unkno.com")



(defn- fact
  "Scrapes the random fact from the webpage."
  []
  (domonad maybe-m
       [resp    (client/get url {:headers header})
        html    (js/parse (:body resp))
        facts   (js/$ html "div[id=content]" (js/text))
        fact    (first facts)]
    fact))


(m/defmodule                                                                                   
  :randomfacts
  0
  (m/defcommand                                                                                
    "fact"                                                                                        
    (fn [instance args msg]
      (when-let [fact (fact)]
        (m/reply instance msg fact)))))
