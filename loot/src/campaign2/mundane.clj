(ns campaign2.mundane
  (:require [campaign2
             [state :refer [armours weapons]]
             [util :as util]]))

(def base-types {"weapon" @weapons "armour" @armours})

(defn find-base [base-name type]
  (first (filter #(= base-name (:name %))
                 (base-types type))))

(defn &base [type]
  (let [bases (util/make-options (group-by :name (base-types type)))]
    (util/display-pairs bases)
    (bases (util/&num))))

(defn new-weapon []
  (util/rand-enabled @weapons))

(defn new-armour []
  (util/rand-enabled @armours))

(defn new []
  (util/rand-enabled (if (> 66 (rand-int 100))
                       @weapons
                       @armours)))
