(ns campaign2.magic
  (:require
    [campaign2
     [util :as util]
     [state :refer [magic-items]]]))

(def rarities {"common"    1
               "uncommon"  2
               "rare"      3
               "very rare" 4
               "artifact"  5
               "legendary" 6})

(defn get-by-rarity [rarity]
  (->> @magic-items
       (filter #(= rarity (:rarity %)))
       (util/rand-enabled)))
