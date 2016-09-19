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

(defmacro defschedule
  [interval handler]
  `{:kind :cronjob :interval ~interval :handler ~handler})


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
         (cond
           (= :storage knd#)
           (body#)
           (= :cronjob knd#)
           (mod/add-cronjob instance# ~name cmd#)
           :else
           (mod/add-handler instance# ~name ~rate cmd#))))))


(defmacro reply
  [instance msg text]
  `((:send-fn (deref ~instance)) ~instance (:channel ~msg) ~text))

(defmacro broadcast
  [instance text]
  `(let [channels# (:joined (deref ~instance))]
     (println channels#)
     (println ~instance)
     (doall
      (map (fn [channel#]
             (println channel#)
             ((:send-fn (deref ~instance)) ~instance channel# ~text))
           channels#))))
