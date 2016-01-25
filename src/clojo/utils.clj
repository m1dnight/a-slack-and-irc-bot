;; Author : Christophe De Troyer
;; Contact: <christophe.detroyer@gmail.com>
;; Date   : 20.01.2016
;; License: MIT
;;;;;;;;;;;;;;;;;;;;;;;;;
;;        _   _ _      ;;
;;  _   _| |_(_) |___  ;;
;; | | | | __| | / __| ;;
;; | |_| | |_| | \__ \ ;;
;;  \__,_|\__|_|_|___/ ;;
;;;;;;;;;;;;;;;;;;;;;;;;;
(ns clojo.utils
  (:require [clojure.edn           :as edn]
            [clojure.java.io       :as  io]
            [clojure.tools.logging :as log]
            [clojure.core.async    :as  as]))



(defn read-config-sysprop
  "Expects a filename for a file to be read from the configuration path. This
  path has to be set as a system property! (E.g.,
  -Dconfig.path=/foo/bar/baz/config."
  [file]
  (let [config-path (System/getProperty "config.path")
        file-path   (io/file config-path file)]
    (if (not (.exists (io/as-file file-path)))
      (log/error "Config" file "does not exist!")
      (edn/read-string (slurp (io/file config-path file))))))


(defn loop-thread-while
  "Takes a predicate and a label. This function will execute the body
  perpetually until the predicate fails."
  [body pred label]
  (let [thread (Thread.
                (fn []
                  (while pred
                    (body))
                  (log/info label " loop exiting!")))]
    (.start thread)
    thread))


(defn read-with-timeout
  "Reads a message from a channel with a timeout. Returns nil if the
  timeout is exceeded."
  [channel timeout]
  (let [res (as/<!!
             (as/go
               (let [[res src] (as/alts! [channel (as/timeout timeout)])]
                 (if (= channel src)
                   res
                   nil))))]
    ;; If res is nil it means that we waited timeout for a message.
    res))
