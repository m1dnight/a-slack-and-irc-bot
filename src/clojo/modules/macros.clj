; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

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


(defmacro reply
  [instance msg text]
  `((:send-fn (deref ~instance)) ~instance (:channel ~msg) ~text))
                                 
