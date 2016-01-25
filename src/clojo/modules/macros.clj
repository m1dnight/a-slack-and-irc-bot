;; Author : Christophe De Troyer
;; Contact: <christophe.detroyer@gmail.com>
;; Date   : 20.01.2016
;; License: MIT
;;  __  __                          
;; |  \/  | __ _  ___ _ __ ___  ___ 
;; | |\/| |/ _` |/ __| '__/ _ \/ __|
;; | |  | | (_| | (__| | | (_) \__ \
;; |_|  |_|\__,_|\___|_|  \___/|___/
(ns clojo.modules.macros
  (:require [clojo.db              :as  db]
            [clojo.modules.modules :as mod]))


(defmacro defcommand
  [trigger handler]
  `{:kind :command :command ~trigger :handler ~handler})


(defmacro defhook
  [type handler]
  `{:kind :hook :hook ~type :handler ~handler})


(defmacro defstorage
  [tablename & fields]
  `(defn ~'init-storage []
     (db/create-table ~tablename ~@fields)))


(defmacro defmodule
  [name rate & cmds]
  `(defn ~'load-module [instance#]
     (doseq [cmd# [~@cmds]]
       (let [knd#  (:kind cmd#)
             body# (:handler cmd#)]
         (if (= :storage knd#)
           (body#)
           (mod/add-handler instance# ~name ~rate cmd#))))))
                                 
