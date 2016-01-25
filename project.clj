(defproject clojo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure       "1.7.0"]
                 [org.clojure/core.async  "0.2.374"]
                 ;; Logging macros -> https://github.com/clojure/tools.logging
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j              "1.2.17" :exclusions [javax.mail/mail
                                                                  javax.jms/jms
                                                                  com.sun.jmdk/jmxtools
                                                                  com.sun.jmx/jmxri]]
                 ;; Http/Websockets
                 [clj-http                  "2.0.1"]
                 [stylefruits/gniazdo       "0.4.1"]
                 ;; Chesire - Encoding/decoding json etc.
                 [cheshire                  "5.5.0"]]
  :main ^:skip-aot clojo.core
  :target-path "target/%s"
  :profiles  {:dev  
              {:jvm-opts ["-Xmx1g" "-Dconfig.path=/Users/m1dnight/.clojo"]}
              :deploy             
              {:jvm-opts ["-Xmx1g" "-Dconfig.path=/home/christophe/.clojo"]}
              :uberjar
              {:aot :all}})
