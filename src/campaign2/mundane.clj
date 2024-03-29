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
  (let [type (if (util/occurred? 2/3) "armour" "weapon")
        mundanes (get base-types type)
        base (case type
               "weapon" (util/rand-enabled mundanes)
               "armour" (let [slot (util/weighted-rand-choice {"body"   3
                                                               "helmet" 3
                                                               "gloves" 3
                                                               "boots"  3
                                                               "shield" 1})]
                          (->> mundanes
                               (filter #(= slot (:slot %)))
                               (rand-nth))))]
    {:base base
     :type type}))
