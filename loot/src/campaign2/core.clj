(ns campaign2.core
  (:require [campaign2
             [util :as util]
             [relic :as relic]
             [mundane :as mundane]
             [enchant :as enchant]
             [crafting :as crafting]
             [consumable :as consumable]
             [state :as state]]
            [clojure.tools.logging :as log]))

(def loot-actions
  {1  {:name   "Negatively enchanted item"
       :action enchant/random-negative-enchanted}
   2  {:name   "Mundane item"
       :action mundane/new}
   3  {:name   "Consumable"
       :action consumable/new}
   8  {:name   "Enchanted item (10 points)"
       :action #(enchant/random-enchanted 10)}
   11 {:name   "Enchanted item (20 points, positive only)"
       :action #(enchant/random-positive-enchanted 20)}
   12 {:name   "Enchanted item (30 points, positive only)"
       :action #(enchant/random-positive-enchanted 30)}
   13 {:name   "Crafting item"
       :action crafting/new}
   21 {:name   "Reload data from files"
       :action state/reload!}
   22 {:name   "Level a relic"
       :action relic/&level!}})

(defn start []
  (while true
    (try
      (util/display-options loot-actions)
      (let [input (util/&num)
            {:keys [action]} (loot-actions input)
            result (when action (action))]
        (when result (println result)))
      (catch Exception e
        (log/errorf e "Unexpected error")
        ))))

(defn -main [& _]
  (start))
