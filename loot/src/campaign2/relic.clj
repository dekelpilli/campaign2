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
  (let [relic (->> @relics
                   (filter (fn [{:keys [found?]}] (not found?)))
                   (util/rand-enabled))]
    (if relic
      (do
        (util/display-multi-value relic)
        (-> (util/&bool false "Mark as found?")
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
         points-remaining (- (* points-per-level (inc level))
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
                                                             (util/rand-enabled))
                                          :new-relic-mod (util/rand-enabled available)
                                          :upgrade-existing-mod (util/rand-enabled upgradeable-mods)
                                          :new-random-mod (->> valid-enchants
                                                               (filter (fn [enchant] (pos? (:points enchant e/default-points))))
                                                               (util/rand-enabled)))]))
                        (into {}))
         mod-options (->> type-mods
                          (map-indexed #(concat [%1] %2))
                          (concat [["Key" "Type" "Value"]]))
         _ (util/display-multi-value mod-options)
         choice (util/&num)
         [_ option-type modifier] (when choice (nth mod-options (inc choice)))]
     (when option-type
       (-> (case option-type
             :new-relic-mod (-> relic
                                (update :available #(filterv (fn [m] (not= modifier m)) %))
                                (update :existing #(conj % modifier)))
             :upgrade-existing-mod (let [upgraded-mod (-> modifier
                                                          (update :level #(if % (inc %) 2))
                                                          ((fn [{:keys [upgrade-points] :as m}]
                                                             (println "Requires manual editing for effect of" m "in" (:name relic))
                                                             (update m :points #(+ upgrade-points %)))))]
                                     (update relic :existing #(map (fn [m] (if (= modifier m) upgraded-mod m)) %)))
             (update relic :existing #(conj % modifier))) ;TODO: add defaulting of upgrade-points
           (update :level inc)
           (override-relic!))))))
