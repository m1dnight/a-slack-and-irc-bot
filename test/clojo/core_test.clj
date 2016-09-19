; Author:  Christophe De Troyer
; Email:   christophe.detroyer@gmail.com
; License: GPLv3
; Date:    May 16, 2016

(ns clojo.core-test
  (:use     [clojure.algo.monads]
            [korma.core         ]
            [korma.db           ])
  (:require [clojo.modules.macros  :as   m]
            [clojo.modules.modules :as mod]
            [clojo.db              :as  db]
            [clojure.tools.logging :as log])
  (:require [clojure.test :refer :all]
            [clojo.core :refer :all]))

(defn foo
  [x]
  x)
