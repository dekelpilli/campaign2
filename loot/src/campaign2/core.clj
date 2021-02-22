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
             [miscreation :as miscreation]
             [encounter :as encounter]
             [dice :as dice]
             [ring :as ring]
             [unique :as unique]
             [state :as state]]
            [clojure.tools.logging :as log]))

(def loot-actions
  {-1 {:name "Exit"}
   1  {:name   "1-10 gold"
       :action #(str (inc (rand-int 10)) " gold")}
   2  {:name   "Trap"
       :action (constantly "TODO")} ;TODO
   3  {:name   "Mundane item"
       :action mundane/new}
   4  {:name   "Miscreation"
       :action miscreation/new}
   5  {:name   "Consumable"
       :action consumable/new}
   6  {:name   "Unique"
       :action unique/new}
   7  {:name   "Enchanted item (10 points)"
       :action #(enchant/random-enchanted 10)}
   8  {:name   "100-150 gold"
       :action #(str (+ 100 (rand-int 51)) " gold")}
   9  {:name   "Ring"
       :action ring/new}
   10 {:name   "Synergy ring"
       :action ring/new-synergy}
   11 {:name   "Enchanted item (20 points, positive only)"
       :action #(enchant/random-enchanted 20)}
   12 {:name   "Enchanted item (30 points, positive only)"
       :action #(enchant/random-enchanted 30)}
   13 {:name   "Crafting item"
       :action crafting/new}
   14 {:name   "Amulet"
       :action monster/generate-amulet}
   15 {:name   "Prayer stone"
       :action prayer/new-stone}
   16 {:name   "New relic"
       :action relic/&new!}
   17 {:name   "Reload data from files"
       :action state/reload!}
   18 {:name   "Level a relic"
       :action relic/&level!}
   19 {:name   "Progress a prayer path"
       :action prayer/&progress!}
   20 {:name   "Choose monsters from given CRs"
       :action monster/&new}
   21 {:name   "Add a modifier to an existing item"
       :action enchant/&add}
   22 {:name   "Add modifiers to an existing items with the given total"
       :action enchant/&add-totalling}
   23 {:name   "Perform a ring sacrifice"
       :action ring/&sacrifice}
   24 {:name   "Sell a relic"
       :action relic/&sell!}
   25 {:name   "Generate random encounters"
       :action encounter/&randomise}
   26 {:name   "Calculate loot rewards"
       :action encounter/&rewards}})

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
