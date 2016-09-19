(defproject clojo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure       "1.7.0"]
                 [org.clojure/core.async  "0.2.374"]
                 [org.clojure/algo.monads   "0.1.5"]
                 [org.clojure/data.json     "0.2.6"]
                 ;; Logging macros -> https://github.com/clojure/tools.logging
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j              "1.2.17" :exclusions [javax.mail/mail
                                                                  javax.jms/jms
                                                                  com.sun.jmdk/jmxtools
                                                                  com.sun.jmx/jmxri]]
                 ;; Can't miss a good Date/Time library!
                 [clj-time                  "0.9.0"]
                 ;; Http/Websockets
                 [clj-http                  "2.0.1"]
                 [stylefruits/gniazdo       "0.4.1"]
                 ;; Chesire - Encoding/decoding json etc.
                 [cheshire                  "5.5.0"]
                 ;; Working with URLs - https://github.com/cemerick/url
                 [com.cemerick/url          "0.1.1"]
                 ;; Database dependencies.
                 [org.xerial/sqlite-jdbc    "3.7.2"]
                 [org.clojure/java.jdbc     "0.3.6"]
                 [clojure-tools             "1.1.3"]
                 [java-jdbc/dsl             "0.1.0"]
                 [korma                     "0.4.0"]
                 ;; Clojurure evaluation
                 [clojail                   "1.0.6"]
                 ;; Web scraping
                 [clj-soup/clojure-soup     "0.1.3"]
                 ;; RSS feeds
                 [adamwynne/feedparser-clj  "0.5.2"]
                 [overtone/at-at            "1.2.0"]
                 ]

  :main ^:skip-aot clojo.core
  :target-path "target/%s"
  :profiles  {:dev
              {:jvm-opts ["-Xmx1g" "-Dconfig.path=/home/christophe/.clojo"]}
              :deploy
              {:jvm-opts ["-Xmx1g" "-Dconfig.path=/home/christophe/.clojo"]}
              :osx
              {:jvm-opts ["-Xmx1g" "-Dconfig.path=/Users/m1dnight/.clojo"]}
              :uberjar
              {:aot :all}})
