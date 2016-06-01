; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.core
  (:require [clojure.tools.logging       :as   log]
            [clojo.chatservices.slackrtm :as slack]
            [clojo.utils                 :as     u]
            [clojo.modules.modules       :as   mod]
            [clojo.modules.plugins.markov])  
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; HELPER ABSTRACTIONS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- parse-command
  "Takes a raw input message and checks if it can pick out a command.
  A command is always formatted as '~<trigger> <args>' where args can
  be empty."
  [msg]
  (try
    (when-let [regex (re-matches #"(,|~)(\w+)\s?(.+?)?([ \t]+)?" msg)]
      {:trigger (keyword (nth regex 2))
       :args    (nth regex 3)})
    (catch NullPointerException e nil)))


(defn await-exits
  "Given a list of instances, will wait for all of them to shut down."
  [instances]
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; MESSAGE HANDLING ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn dispatcher
  "Parses a message and checks if it is a command (e.g., ~youtube
  movietitle). If it is a command the proper handlers are executed.
  Finally, all hooks are executed as well according to the type of
  this message. (e.g., privmsg)."
  [instance msg]
  (let [command (parse-command (:message msg))
        msgtype (:command msg)]
    ;; Trigger the command if the case. (I.e., modules that are triggered with a
    ;; command).
    (when-let [{t :trigger a :args} command]
      (mod/apply-triggers instance t instance a msg))
    ;; Activate all listeners (i.e., plugins that just listen to all messages).
    (mod/apply-listeners instance msgtype instance msg)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; MAIN ENTRY POINT ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn -main
  "Reads the clojo.edn config file and then instantiates all the bots that are
  specified there."
  [& args]
  (clojo.modules.plugins.markov/read-from-file "filtered.txt")
  (let [clojo-cfg (u/read-config-sysprop "clojo.edn")
        instances (doall
                   (map (fn [instance]
                          (let [instance-cfg (u/read-config-sysprop instance)
                                instance     (case (:type instance-cfg)
                                               ;; Create a Slack RTM instance.
                                               :slack-rtm
                                               (do (log/info "Connecting to slack instance" (:name instance-cfg))
                                                   (slack/init-connection (slack/create-instance instance-cfg dispatcher)))
                                               ;; Create a plain old IRC connection.
                                               :irc
                                               (log/info "Connecting IRC")
                                               ;; Not defined.
                                               (log/error "Unknown instance!"))]
                            ;; Load all modules defined in the instance.
                            (doseq [module (:modules instance-cfg)]
                              (mod/load-module instance module))))
                        (:instances clojo-cfg)))]
    ;; Finally, wait for all the connections to die.
    ;; *This is blocking!*
    (await-exits instances)))
