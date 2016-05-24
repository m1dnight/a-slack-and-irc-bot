; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

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
                  (log/debug label " loop exiting!")))]
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


(defn rand-bool
  "Generates a random boolean value."
  []
  (= 0 (rand-int 2)))


(defn keywordize-keys
  "Recursively transforms all map keys from strings to keywords."
  [m]
  (let [f (fn [[k v]]
            (if (string? k)
              [(keyword k) v]
              [k v]))]
    (clojure.walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))


(defn replace-several
  "Replaces serveral chars with their replacement in a string."
  [content & replacements]
      (let [replacement-list (partition 2 replacements)]
        (reduce #(apply clojure.string/replace %1 %2) content replacement-list)))

(defn positive?
  [x]
  (> x 0))

(defn negative?
  [x]
  (not (positive? x)))
