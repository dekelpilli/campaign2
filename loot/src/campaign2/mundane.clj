(ns campaign2.mundane
  (:require [campaign2
             [state :refer [armours weapons]]
             [util :as util]]))

(def base-types {"weapon" @weapons "armour" @armours})

(defn find-base [base-name type]
  (first (filter #(= base-name (:name %))
                 (base-types type))))

(defn &base
  ([]
   (let [choice (util/&choose (keys base-types))]
     (when choice
       {:base (&base choice)
        :type choice})))
  ([type]
   (-> (group-by :name (base-types type))
       (util/&choose {:sort? true :v "Base"})
       (first))))

(defn new-weapon []
  (util/rand-enabled @weapons))

(defn new-armour []
  (util/rand-enabled @armours))

(defn new []
  (util/rand-enabled (if (util/occurred? 66)
                       @armours
                       @weapons)))
