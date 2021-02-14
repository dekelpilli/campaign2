(ns campaign2.relic
  (:require [campaign2
             [state :refer [relics override-relics! character-enchants]]
             [util :as util]
             [enchant :as e]
             [mundane :as mundane]]))

(def ^:private points-per-level 10)

;TODO: add func for selling (mark as enabled?=false, give price)
(def prices [0 75 200 300 600 800 1100 1300 1500 2000])

(defn- &choose-relic [relics]
  (let [name-relics (->> relics
                         (map (fn [relic] [(:name relic) relic]))
                         (into {}))
        relic-options (util/make-options name-relics)]
    (when (not-empty relic-options)
      (util/display-pairs relic-options)
      (-> (util/&num)
          (relic-options)
          (name-relics)))))

(defn- &owned []
  (let [owned? (fn [{:keys [found? enabled?]}] (and found? enabled?))]
    (->> @relics
         (filter owned?)
         (&choose-relic))))

(defn- &upgradeable []
  (let [upgradeable? (fn [{:keys [found? enabled? level]}] (and found? enabled? (<= level 10)))]
    (->> @relics
         (filter upgradeable?)
         (&choose-relic))))

(defn- override-relic! [{:keys [name] :as relic}]
  (override-relics! (mapv #(if (= (:name %) name) relic %) @relics)))

;TODO: test :none, multi-stage upgrades, character-specific mods
(defn &level!
  ([] (let [relic (&upgradeable)]
        (when relic (&level! relic))))
  ([{:keys [level existing base type available progressed owner] :as relic}]
   (let [points-remaining (- (* points-per-level (inc level))
                             (reduce + (map :points existing))
                             (reduce + (map :progress progressed)))
         upgradeable-mods (filter #(:upgradeable? % true) existing)
         possible-options (cond-> [:new-relic-mod :new-random-mod :new-character-mod]
                                  (seq upgradeable-mods) (conj :upgrade-existing-mod))
         upgrade-options (conj (take 2 (concat progressed (repeatedly #(rand-nth possible-options))))
                               :none)
         valid-enchants (e/find-valid-enchants (mundane/find-base base type) type)
         rand-filled #(->> % util/rand-enabled util/fill-randoms)
         type-mods (->> upgrade-options
                        (map (fn [o] [o (rand-filled
                                          (case o
                                            :new-character-mod (@character-enchants owner)
                                            :new-relic-mod available
                                            :upgrade-existing-mod upgradeable-mods
                                            :new-random-mod valid-enchants
                                            :none [nil]))]))
                        (into {}))
         mod-options (->> type-mods
                          (map-indexed #(concat [%1] %2))
                          (concat [["Key" "Type" "Value"]])
                          (util/display-multi-value))
         choice (util/&num)
         [_ option-type modifier] (when (and choice (>= choice 0)) (nth mod-options (inc choice)))
         attach-new-mod (fn [{:keys [points upgrade-points]
                              :or   {points 10}} modifier]
                          ;new random/player mods are always added as if they have max 10 points
                          (update relic :existing
                                  #(conj % (assoc modifier
                                             :level 1
                                             :points (min points 10)
                                             :upgrade-points (or upgrade-points points)))))
         upgrade-mod (fn [{:keys [committed upgrade-points effect]
                           :or   {committed 0} :as modifier}]
                       (let [selected-mod-effect effect]
                         (if (>= (+ committed points-remaining) upgrade-points)
                           (-> relic
                               (update :progressed #(filterv (fn [{:keys [effect]}]
                                                               (not= selected-mod-effect effect)) %))
                               (update :existing #(mapv (fn [{:keys [effect] :as existing-mod}]
                                                          (if (= selected-mod-effect effect)
                                                            (-> existing-mod
                                                                (update :points (fn [points] (+ upgrade-points points)))
                                                                (update :level inc))
                                                            existing-mod)) %)))
                           (update relic :progressed
                                   #(as-> % $
                                          (filterv (fn [{:keys [effect]}]
                                                     (not= selected-mod-effect effect)) $)
                                          (conj $ (assoc modifier :committed (+ committed points-remaining))))))))]
     (when option-type
       (-> (case option-type
             :new-random-mod (attach-new-mod modifier)
             :new-character-mod (attach-new-mod modifier)
             :new-relic-mod (-> relic
                                (update :available #(filterv (fn [m] (not= modifier m)) %))
                                (update :existing #(conj % modifier)))
             :upgrade-existing-mod (upgrade-mod modifier)
             :none relic)
           (update :level inc)
           (override-relic!))))))

(defn &new! []
  (let [relic (->> @relics
                   (filter (fn [{:keys [found?]}] (not found?)))
                   (util/rand-enabled))]
    (if relic
      (util/display-multi-value relic)
      (throw (Exception. "Out of relics :(")))
    (let [base (mundane/&base (:type relic))
          owner (when base nil)] ;TODO choose owner based on keys of character-enchants
      (when (and base owner)
        (override-relic! (assoc relic :found? true
                                      :base base
                                      :owner owner))))))
