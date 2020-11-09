(ns campaign2.mundane
  (:require [campaign2
             [state :refer [*armours* *weapons*]]
             [util :as util]]))

(defn find-base [base-name type]
  (first (filter #(= base-name (:name %))
                 ({:weapon *weapons* :armour *armours*} type))))

(defn new-weapon []
  (rand-nth *weapons*))

(defn new-armour []
  (rand-nth *armours*))

(defn new []
  (rand-nth (rand-nth [*weapons* *armours*])))
