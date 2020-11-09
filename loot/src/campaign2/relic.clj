(ns campaign2.relic
  (:require [campaign2
             [state :refer [*relics* override-relics!]]
             [util :as util]
             [enchant :as enchant]]
            [clojure.tools.logging :as log]))

(def ^:private points-per-level 10)

(defn- &upgradeable []
  (let [upgradeable? (fn [{:keys [found? enabled? level]}] (and found? enabled? (<= level 10)))
        upgradeable-relics (->> *relics*
                                (filter upgradeable?)
                                (map (fn [relic] [(:name relic) relic]))
                                (into {}))
        relic-options (->> upgradeable-relics
                           (keys)
                           (map-indexed (fn [i name] [i name]))
                           (into {}))]
    (when (not-empty relic-options)
      (log/infof "Relic options: %s" (clojure.pprint/write relic-options))
      (-> (util/&num)
          (relic-options)
          (upgradeable-relics)))))

(defn- override-relic! [{:keys [name] :as relic}]
  (override-relics! (map #(if (= (:name %) name) relic %))))

(defn &new! []
  (let [relic (filter (fn [{:keys [enabled? found?]}] (and enabled? (not found?))) *relics*)
        options {1 true 2 false}]
    (log/infof "Found relic: %s" (clojure.pprint/write relic))
    (log/infof "Mark as found? %s" options)
    (-> (util/&num)
        (options)
        (when (override-relic! (assoc relic :found? true))))))

(defn level-relic!
  ([] (let [relic (&upgradeable)]
        (when relic (level-relic! relic))))
  ([{:keys [level existing base type available] :as relic}]
   (let [current-points-total (-> (map :points existing)
                                  (reduce +))
         points-remaining (- (* points-per-level (+ level 1))
                             current-points-total)
         upgrade-options (if-not (neg? points-remaining)
                           (let [upgradeable-mods (filter :upgradeable? existing)]
                             '(:new-relic-mod
                                :new-random-mod
                                (if (empty? upgradeable-mods) (rand-nth [:new-relic-mod :new-random-mod]) (:upgrade-existing-mod))))
                           (repeatedly :negative-mod 3))
         valid-enchants (enchant/find-valid-enchants (campaign2.mundane/find-base base type) type)
         mod-options (map #(case %
                             :negative-mod (->> valid-enchants
                                                (filter (fn [enchant] (neg? (:points enchant))))
                                                (rand-nth))
                             :new-relic-mod (rand-nth available)
                             :upgrade-existing-mod (rand-nth existing)
                             :new-random-mod (->> valid-enchants
                                                  (filter (fn [enchant] (pos? (:points enchant))))
                                                  (rand-nth)))
                          upgrade-options)]
     ;TODO present options from mod-options, persist choice and reload relics
     )))
