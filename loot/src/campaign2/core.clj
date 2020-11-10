(ns campaign2.core
  (:require [campaign2
             [util :as util]
             [relic :as relic]
             [mundane :as mundane]
             [enchant :as enchant]
             [crafting :as crafting]
             [consumable :as consumable]
             [prayer :as prayer]
             [state :as state]]
            [clojure.tools.logging :as log]))

(def loot-actions
  {-1 {:name "Exit"}
   1  {:name   "Negatively enchanted item"
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
   20 {:name   "New relic"
       :action relic/&new!}
   21 {:name   "Reload data from files"
       :action state/reload!}
   22 {:name   "Level a relic"
       :action relic/&level!}
   23 {:name   "Progress a prayer path"
       :action prayer/&progress!}})

(defn start []
  (let [loot-action-names (->> loot-actions
                               (map (fn [e] [(key e) (:name (val e))]))
                               (into {}))]
    (loop [input (atom nil)]
      (try
        (when-not (contains? loot-actions @input) (util/display-options loot-action-names))
        (reset! input (util/&num))
        (let [{:keys [action]} (loot-actions @input)
              result (when action (action))]
          (cond
            (string? result) (println result)
            (seqable? result) (doseq [r result] (util/display-result r))
            :else (when result (util/display-result result))))
        (catch Exception e
          (log/errorf e "Unexpected error")))
      (when (or (nil? @input) (pos? @input))
        (recur input)))))

(defn -main [& _]
  (start))
