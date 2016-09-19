; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.modules.plugins.xkcd
  (:require [clojure.tools.logging :as    log]
            [clojo.modules.macros  :as      m]))

(def xkcd "http://xkcd.com/rss.xml")
