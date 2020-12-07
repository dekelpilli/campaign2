(ns campaign2.core
  (:require [campaign2
             [util :as util]
             [relic :as relic]
             [mundane :as mundane]
             [enchant :as enchant]
             [crafting :as crafting]
             [consumable :as consumable]
             [prayer :as prayer]
             [monster :as monster]
             [magic :as magic]
             [dice :as dice]
             [ring :as ring]
             [state :as state]]
            [clojure.tools.logging :as log]))

(def loot-actions
  {-1 {:name "Exit"}
   1  {:name   "Negatively enchanted item"
       :action enchant/random-negative-enchanted}
   2  {:name   "1-20 gold"
       :action #(str (inc (rand-int 20)) " gold")}
   3  {:name   "Mundane item"
       :action mundane/new}
   4  {:name   "Common magic item"
       :action #(magic/get-by-rarity "common")}
   5  {:name   "Uncommon magic item"
       :action #(magic/get-by-rarity "uncommon")}
   6  {:name   "Consumable"
       :action consumable/new}

   8  {:name   "Enchanted item (10 points)"
       :action #(enchant/random-enchanted 10)}
   9  {:name   "100-150 gold"
       :action #(str (+ 100 (rand-int 51)) " gold")}
   10 {:name   "Ring"                                       ;todo higher?
       :action ring/new}
   11 {:name   "Enchanted item (20 points, positive only)"
       :action #(enchant/random-positive-enchanted 20)}
   12 {:name   "Enchanted item (30 points, positive only)"
       :action #(enchant/random-positive-enchanted 30)}
   13 {:name   "Crafting item"
       :action crafting/new}
   14 {:name   "Amulet"
       :action monster/generate-amulet}

   19 {:name   "Prayer stone"
       :action prayer/new-stone}
   20 {:name   "New relic"
       :action relic/&new!}
   21 {:name   "Reload data from files"
       :action state/reload!}
   22 {:name   "Level a relic"
       :action relic/&level!}
   23 {:name   "Progress a prayer path"
       :action prayer/&progress!}
   24 {:name   "Choose monsters from given CRs"
       :action monster/&new}
   25 {:name   "Add a modifier to an existing item"
       :action enchant/&add}
   26 {:name   "Add modifiers to an existing items with the given total"
       :action enchant/&add-totalling}})

(defn start []
  (let [loot-action-names (->> loot-actions
                               (map (fn [e] [(key e) (:name (val e))]))
                               (into {}))]
    (util/display-pairs loot-action-names)
    (loop [action (atom nil)]
      (try
        (let [input (read-line)
              num-input (util/->num input)
              pos-num? (and num-input (pos? num-input))
              dice-input (when-not pos-num? (dice/parse input))
              _ (reset! action (or (:action (loot-actions num-input))
                                   (when dice-input #(dice/roll dice-input))
                                   (when pos-num? (constantly loot-action-names))))
              result (when @action (@action))]
          (cond
            (string? result) (println result)
            (map? result) (util/display-pairs result)
            (seqable? result) (doseq [r result] (util/display-multi-value r))
            :else (when result (util/display-multi-value result))))
        (catch Exception e
          (log/errorf e "Unexpected error")))
      (when @action
        (recur action)))))

(defn -main [& _]
  (start))
