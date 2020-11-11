(ns campaign2.relic
  (:require [campaign2
             [state :refer [relics override-relics!]]
             [util :as util]
             [enchant :as e]]
            [clojure.tools.logging :as log]))

(def ^:private points-per-level 10)

(defn- &upgradeable []
  (let [upgradeable? (fn [{:keys [found? enabled? level]}] (and found? enabled? (<= level 10)))
        upgradeable-relics (->> @relics
                                (filter upgradeable?)
                                (map (fn [relic] [(:name relic) relic]))
                                (into {}))
        relic-options (util/make-options upgradeable-relics)]
    (when (not-empty relic-options)
      (util/display-pairs relic-options)
      (-> (util/&num)
          (relic-options)
          (upgradeable-relics)))))

(defn- override-relic! [{:keys [name] :as relic}]
  (override-relics! (mapv #(if (= (:name %) name) relic %) @relics)))

(defn &new! []
  (let [relic (first (filter (fn [{:keys [enabled? found?]}] (and enabled? (not found?))) @relics))
        options {1 true 2 false}]
    (if relic
      (do
        (util/display-multi-value relic)
        (util/display-pairs "Mark as found?" options)
        (-> (util/&num)
            (options)
            (when (override-relic! (assoc relic :found? true))) ;TODO: attunement process (choose base, level)
            ))
      "You're out of relics!")))

;TODO: think about allowing relics to go into negatives. If not, probably best to always give an option for doing nothing and an option for negative mods
(defn &level!
  ([] (let [relic (&upgradeable)]
        (when relic (&level! relic))))
  ([{:keys [level existing base type available] :as relic}]
   (let [current-points-total (->> (map :points existing)
                                   (reduce +))
         points-remaining (- (* points-per-level (+ level 1))
                             current-points-total)
         upgradeable-mods (filter #(:upgradeable? % true) existing)
         upgrade-options (if-not (neg? points-remaining)
                           (conj '(:new-relic-mod :new-random-mod)
                                 (if (empty? upgradeable-mods) (rand-nth [:new-relic-mod :new-random-mod]) :upgrade-existing-mod))
                           (repeatedly :negative-mod 3))
         valid-enchants (e/find-valid-enchants (campaign2.mundane/find-base base type) type)
         type-mods (->> upgrade-options
                          (map (fn [o] [o (case o
                                            :negative-mod (->> valid-enchants
                                                               (filter (fn [enchant] (neg? (:points enchant e/default-points))))
                                                               (rand-nth))
                                            :new-relic-mod (rand-nth available)
                                            :upgrade-existing-mod (rand-nth upgradeable-mods)
                                            :new-random-mod (->> valid-enchants
                                                                 (filter (fn [enchant] (pos? (:points enchant e/default-points))))
                                                                 (rand-nth)))]))
                          (into {}))
         mod-options (->> type-mods
                          (map-indexed #(concat [%1] %2))
                          (concat [["Key" "Type" "Value"]]))
         _ (util/display-multi-value mod-options)
         choice (util/&num)
         [_ type value] (when choice (nth type choice))]
     ;TODO update relics and persist
     )))
