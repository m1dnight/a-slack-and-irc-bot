;; Author : Christophe De Troyer
;; Contact: <christophe.detroyer@gmail.com>
;; Date   : 20.01.2016
;; License: MIT
;;  __  __           _       _           
;; |  \/  | ___   __| |_   _| | ___  ___ 
;; | |\/| |/ _ \ / _` | | | | |/ _ \/ __|
;; | |  | | (_) | (_| | |_| | |  __/\__ \
;; |_|  |_|\___/ \__,_|\__,_|_|\___||___/
(ns clojo.modules.modules
  (:require [clojure.tools.logging :as log]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; LOADING PLUGINS ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn load-module
  "Takes the instance and the string representation of a module. Loads the
  module and attaches it to the instance."
  [instance name]
  (log/debug "Loading plugin" name "for" (:name instance))
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
    (log/debug "Done..")))


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
          (log/debug "Applying command" name "Limited: " (limited? instance name rl))
          (println "args" args)
          (apply handler args)
          (catch Exception e
            (log/error "Command handler " handler " threw an exception: " (.getMessage e) "\n" (.getStacktrace e))))
        ;; Reset the activity for the module.
        (update-last-activity instance name)))))
