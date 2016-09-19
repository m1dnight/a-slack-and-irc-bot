; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.core
  (:require [clojure.tools.logging       :as   log]
            [clojo.chatservices.slackrtm :as slack]
            [clojo.chatservices.irc      :as   irc]
            [clojo.utils                 :as     u]
            [clojo.modules.modules       :as   mod]
            [overtone.at-at              :as  atat]
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
;;; COMMON FIELDS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def scheduler (atat/mk-pool))

(def second   1000)
(def debug    (* 5 second))
(def minute   (* 60 second))
(def hour     (* 60 minute))
(def day      (* 24 hour))
(def week     (* 7 day))
(def delay    60000)


(defn insert-common-fields
  "Attaches the common fields between instances"
  [instance]
  (dosync
   (alter instance #(assoc %
                           :scheduler scheduler
                           :cron      {:debug    []
                                       :minutely []
                                       :hourly   []
                                       :daily    []
                                       :weekly   []}))))


(defn exec-all-with
  "Executes a list of functions"
  [instance fs]
  (doall
   (map (fn [f]
          (f instance))
        fs)))


(defn exec-crons
  [instance interval]
  (log/info (:name @instance) "Executing cronjobs: " interval)
  (let [crons (interval (:cron @instance))]
    (doall (map #(%) crons))))

(defn register-cronjobs
  "Reigstered all the known cronjobs with the scheduler with a fixed
  delay to make sure they don't fire before the bot has joined
  channels."
  [instance]
  (let [crons (:cron @instance)]
    (log/info (:name @instance) "Cronjobs:" crons)
    (atat/every debug  #(exec-crons instance :debug)    scheduler :initial-delay delay)
    (atat/every minute #(exec-crons instance :minutely) scheduler :initial-delay delay)
    (atat/every hour   #(exec-crons instance :hourly)   scheduler :initial-delay delay)
    (atat/every day    #(exec-crons instance :daily)    scheduler :initial-delay delay)
    (atat/every week   #(exec-crons instance :weekly)   scheduler :initial-delay delay)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; MAIN ENTRY POINT ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn -main
  "Reads the clojo.edn config file and then instantiates all the bots that are
  specified there."
  [& args]
                                        ;(clojo.modules.plugins.markov/read-from-file "filtered.txt")
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
                                               (do (log/info "Connecting IRC")
                                                   (irc/init-connection (irc/create-instance instance-cfg dispatcher)))
                                               ;; Not defined.
                                               (log/error "Unknown instance!"))]
                            ;; Attach scheduler to instances. This HAS
                            ;; to happen before the modules are
                            ;; loaded, because modules depend on it.
                            (insert-common-fields instance)
                            ;; Load all modules defined in the instance.
                            (doseq [module (:modules instance-cfg)]
                              (mod/load-module instance module))
                            ;; Register the cronjobs
                            (register-cronjobs instance)
                            instance))
                        (:instances clojo-cfg)))]
    ;; Finally, wait for all the connections to die.
    ;; *This is blocking!*
    (await-exits instances)))
