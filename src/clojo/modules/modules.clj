; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.modules.modules
  (:require [clojure.string        :as string]
            [clojure.tools.logging :as    log]
            [overtone.at-at        :as   atat]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; LOADING PLUGINS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn load-module
  "Takes the instance and the string representation of a module. Loads the
  module and attaches it to the instance."
  [instance name]
  (log/info "Loading plugin" name "for" (:name @instance))
  (let [fullns (symbol (str "clojo.modules.plugins." name))
        modfn  (symbol (str "clojo.modules.plugins." name "/load-module"))
        storfn (symbol (str "clojo.modules.plugins." name "/init-storage"))]
    (require fullns :reload)
    ;; Execute optional storage intialisation.
    (when (resolve storfn)
      ((resolve storfn)))
    ;; Execute the macro function in the module by giving it the servers.
    (when (resolve modfn)
      ((resolve modfn) instance))
    (log/debug name " loaded!")))


(defn add-handler
  " Adds a command or hook to the instance. This function should only be used by
  the module dispatcher!"
  [instance name rate module]
  (let [handler  (:handler module)
        kind     (:kind module) ;; :command or a :hook
        ;; Each module is either a hook or a command.
        ;; A command has  a :command defined (e.g., "youtube")
        ;; and a hook has a hook defined (e.g., :PRIVMSG).
        ;; So the two below are mutually exclusive!
        command  (:command module)
        hook     (:hook module)
        submap   (keyword (if command command hook))]
    ;; The kind: :command or :hook
    ;; Create new channel for this module.
    ;; Insert the handler in the proper sublist in the bot.
    (dosync
     (alter instance
            (fn [instance]
              (update-in instance
                         [kind submap]
                         conj {:name name :f handler :rate rate}))))))


(defn add-cronjob
  "Adds a cronjob to the instance. This function should only be used
  by the macros."
  [instance name command]
  (let [handler       #((:handler command) instance)
        interval      (:interval command)]
    (dosync
     (alter instance
            (fn [instance]
              (let [allowed (keys (:cron instance))]
                ;; Check if the interval is allowed (i.e., if it is a
                ;; key in the cron map).
                (if (some #{interval} allowed)
                  (update-in instance [:cron interval] #(conj % handler))
                  (log/error (:name instance) "Invalid interval in cronjob!"))))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; RATE LIMITING PLUGINS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn ratelimit-map
  "Returns the hashmap that contains timestamps for modules"
  [instance]
  (:ratelimits @instance))


(defn update-last-activity
  "Updates the last activity stamp of the given name in the map."
  [instance name]
  (let [limit-map   (ratelimit-map instance)
        last-active (name limit-map)
        now         (System/currentTimeMillis)]
    (dosync
     (alter instance
            (fn [instance]
              (update-in instance
                         [:ratelimits]
                         (fn [m] (assoc m name now))))))))


(defn last-activity
  "Updates the last activity stamp of the given name in the map."
  [instance name]
  (let [limit-map   (ratelimit-map instance)
        last-active (or (name limit-map) 0)]
    last-active))


(defn limited?
  "Given the name of a module determines if the module is hitting its ratelimit
  or not."
  [instance name rate]
  (let [last-active (last-activity instance name)
        now         (System/currentTimeMillis)]
    (< (- now last-active) rate)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; ACTIVATING PLUGINS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn apply-triggers
  "Takes a instance, a keyword and arguments. For each handler that is
  associated with the keyword in the command map, the handler will be
  applied to the given arguments. In case of an exception an error message is printed to stdout."
  [instance kind & args]
  (let [handlermap (:command @instance)
        handlers   (kind handlermap)]
    (doseq [{handler :f name :name rl :rate} handlers]
      ;; Only if the ratelimit allows us to execute the module, execute it.
      (when-not (limited? instance name rl)
        (try
          (apply handler args)
          (catch Exception e
            (log/error "Command handler" handler "threw an exception:\n" e "\nERROR END")))
        ;; Reset the activity for the module.
        (update-last-activity instance name)))))


(defn apply-listeners
  "Given a kind and arguments, this function will take out all the
  handlers in the hooks map that are bound to the keyword. It will
  then apply each handler to the given arguments. In case of an
  exception the handler an error message is printed to stdout."
  [instance kind & args]
  (let [limits   (ratelimit-map instance)
        hooks    (:hook @instance)
        handlers (kind hooks)]
    (doseq [{handler :f name :name rl :rate} handlers]
      (log/trace "Applying listener" name "Limited: " (limited? instance name rl))
      ;; Only if the ratelimit allows us to execute the module, execute it.
      (when-not (limited? instance name rl)
        (try
          (apply handler args)
          (catch Exception e
            (log/error "Hook" handler "threw an exception:\n" e "\nERROR END")))
        ;; Reset the activity for the module.
        (update-last-activity instance name)))))
