(ns campaign2.relic
  (:require [campaign2
             [state :refer [relics override-relics!]]
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

(defn &owned []
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

;TODO: think about allowing relics to go into negatives. If not, probably best to always give an option for doing nothing and an option for negative mods(?)
;TODO: implement multi-stage mod levelling with guaranteed offering
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
                                                             (util/rand-enabled)
                                                             (util/fill-randoms))
                                          :new-relic-mod (util/rand-enabled available)
                                          :upgrade-existing-mod (->> upgradeable-mods
                                                                     (util/rand-enabled)
                                                                     (util/fill-randoms))
                                          :new-random-mod (->> valid-enchants
                                                               (filter (fn [enchant] (pos? (:points enchant e/default-points))))
                                                               (util/rand-enabled)
                                                               (util/fill-randoms)))]))
                        (into {}))
         mod-options (->> type-mods
                          (map-indexed #(concat [%1] %2))
                          (concat [["Key" "Type" "Value"]])
                          (util/display-multi-value))
         choice (util/&num)
         [_ option-type modifier] (when (and choice (>= choice 0)) (nth mod-options (inc choice)))
         prep-relic-mod (fn [{:keys [points upgrade-points] :as m}]
                          (assoc m
                            :points (or points 10)
                            :upgrade-points (or points upgrade-points 10)
                            :level 1
                            :upgradeable? true))]
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
             (update relic :existing #(conj % (prep-relic-mod modifier))))
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
          updated-relic (-> relic
                            (assoc :found? true)
                            (assoc :base base))]
      (when updated-relic
        (override-relic! updated-relic)))))
