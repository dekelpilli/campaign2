(ns campaign2.mundane
  (:require [campaign2
             [state :refer [armours weapons]]
             [util :as util]]))

(def base-types {"weapon" @weapons "armour" @armours})

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

(defn new []
  (let [type (if (util/occurred? 2/3) "armour" "weapon")]
    {:base (util/rand-enabled (get base-types type))
     :type type}))
