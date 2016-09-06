; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.modules.plugins.cljeval
  (:require [clojure.tools.logging :as    log]
            [cemerick.url          :as    url]
            [clj-http.client       :as client]
            [clojo.modules.macros  :as      m]
            [clojail.core          :refer [sandbox]]
            [clojail.testers       :refer [blacklist-symbols blacklist-objects]])
  (:import   java.io.StringWriter))


;;;;;;;;;;;;;;;
;; Evaluator ;;
;;;;;;;;;;;;;;;

;; Sandbox definition.
(def tester [(blacklist-symbols #{'alter-var-root})
             (blacklist-objects [java.lang.Thread])])
(def sb (sandbox tester :timeout 5000))


(defn eval-expr
  "Evaluate the given string"
  [s]
  (try
    (with-open [out (StringWriter.)]
      (let [form (binding [*read-eval* false] (read-string s))
            result (sb form {#'*out* out})]
        {:status true
         :input s
         :form form
         :result result
         :output (.toString out)}))
    (catch Exception e
      {:status false
       :input s
       :result (.getMessage e)})))


(defn format-result-slack
  "Formats the output for Slack."
  [r]
  (if (:status r)
    (str "```"
         "=> " (:form r) "\n"
         (when-let [o (:output r)]
           o)
         (if (nil? (:result r))
           "nil"
           (:result r))
         "```")
    (str "```"
         "==> " (or (:form r) (:input r)) "\n"
         (or (:result r) "Unknown Error")
         "```")))


(defn format-result-irc
  "Formats the output for Slack."
  [r]
  (if (:status r)
    (str (:form r)
         " ==> "
         (when-let [o (:output r)]
           o)
         (if (nil? (:result r))
           "nil"
           (:result r)))
    (str (or (:form r) (:input r)) " ==> "
         (or (:result r) "Unknown Error"))))

(defn format-result
  [instance evalres]
  (let [type (:type @instance)
        frmt
        (if
          (= :slack type)
          format-result-slack
          format-result-irc)]
    (frmt evalres)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; MODULE DEFINITION ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(m/defmodule
  :karma
  0
  (m/defcommand
    "eval"
    (fn [instance args msg]
      (when-let [evalres (eval-expr args)]
        (m/reply instance msg (format-result instance evalres))))))
