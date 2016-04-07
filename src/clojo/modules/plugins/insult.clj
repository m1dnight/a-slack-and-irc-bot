(ns clojo.modules.plugins.insult
  (:use     [clojure.algo.monads])
  (:require [clojure.tools.logging :as    log]
            [clojure.string        :as    str]
            [cemerick.url          :as    url]
            [clj-http.client       :as client]
            [clojo.modules.macros  :as      m]
            [jsoup.soup            :as     js]))


(def header {"User-Agent"      "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:24.0) Gecko/20100101 Firefox/24.0"
             "Accept"          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
             "Accept-Encoding" "gzip, deflate"
             "Accept-Language" "en-US,en;q=0.5"
             "Connection"      "keep-alive"})

(def url "http://www.insultgenerator.org/")



(defn- insult
  "Scrapes the insult from the webpage."
  []
  (domonad maybe-m
       [resp    (client/get url {:headers header})
        html    (js/parse (:body resp))
        insults (js/$ html "div[class=wrap]" (js/text))
        insult  (first insults)]
    insult))


(m/defmodule                                                                                   
  :insults
  0
  (m/defcommand                                                                                
    "insult"                                                                                        
    (fn [instance args msg]
      (when args
        (domonad maybe-m
                 [splitted (str/split args #" ")
                  insultee (str/capitalize (first splitted))
                  inslt    (insult)
                  ins      (str/lower-case inslt)]
                 (m/reply instance msg (format "%s, %s" insultee ins)))))))
