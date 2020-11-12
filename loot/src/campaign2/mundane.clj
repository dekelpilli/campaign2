(ns campaign2.mundane
  (:require [campaign2
             [state :refer [armours weapons]]
             [util :as util]]))

(defn find-base [base-name type]
  (first (filter #(= base-name (:name %))
                 ({"weapon" @weapons "armour" @armours} type))))

(defn new-weapon []
  (util/rand-enabled @weapons))

(defn new-armour []
  (util/rand-enabled @armours))

(defn new []
  (util/rand-enabled (if (> 66 (rand-int 100))
                       @weapons
                       @armours)))
