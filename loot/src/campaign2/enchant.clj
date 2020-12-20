(ns campaign2.enchant
  (:require
    [clojure.set :as s]
    [campaign2
     [state :refer [enchants]]
     [util :as util]
     [mundane :as mundane]]))

(def default-points 10)

(defn- compatible? [base enchant field]
  (let [not-field (->> field
                       (name)
                       (str "not-")
                       (keyword))
        item-field-val (as-> (base field) $
                             (if (coll? $) (set $) #{$}))
        requisites (enchant field)
        incompatibles (enchant not-field)]
    (and (empty? (s/intersection item-field-val incompatibles))
         (or (empty? requisites)
             (not-empty (s/intersection item-field-val requisites))))))

(defn- compatible-weapon? [{:keys [metadata range] :as base}
                           {:keys [ranged?] :as enchant}]
  (and (or (empty? metadata)
           (contains? metadata "weapon"))
       (if-not (nil? ranged?)
         (= ranged? (boolean range))
         true)
       (compatible? base enchant :traits)
       (compatible? base enchant :category)
       (compatible? base enchant :damage-types)
       (compatible? base enchant :proficiency)
       (compatible? base enchant :type)))

(defn- compatible-armour? [{:keys [metadata] :as base}
                           enchant]
  (and (or (empty? metadata)
           (contains? metadata "armour"))
       (compatible? base enchant "disadvantaged-stealth")
       (compatible? base enchant "type")))

(defn find-valid-enchants [base type]
  (let [enabled-enchants (filter #(:enabled? % true) @enchants)]
    (case type
      "weapon" (filter #(compatible-weapon? base %) enabled-enchants)
      "armour" (filter #(compatible-armour? base %) enabled-enchants))))

(defn- add-enchants [base type points-target points-validator]
  (let [points-comparator (if (neg? points-target) > <)
        valid-enchants (->> (find-valid-enchants base type)
                            (filter #(points-validator (:points % default-points)))
                            (shuffle))
        sum (atom 0)
        enchants (->> valid-enchants
                      (filter #(and (points-comparator @sum points-target)
                                    (swap! sum (partial + (:points % default-points)))))
                      (map util/fill-randoms))]
    [base enchants]))

(defn- random-x-enchanted [points-target points-validator]
  (let [type (rand-nth ["weapon" "armour"])
        base (case type
               "armour" (mundane/new-armour)
               "weapon" (mundane/new-weapon))]
    (add-enchants base type points-target points-validator)))

(defn random-enchanted [points-target]
  (random-x-enchanted points-target (constantly true)))

(defn random-positive-enchanted [points-target]
  (random-x-enchanted points-target pos?))

(defn random-negative-enchanted []
  (random-x-enchanted (- 20 (rand-int -21)) neg?))

(defn &add []
  (let [{:keys [base type]} (mundane/&base)]
    (when (and base type)
      (util/rand-enabled (find-valid-enchants base type)))))

(defn &add-totalling []
  (let [randomise-enchants (fn [points {:keys [base type] :as input}]
                             (if input
                               (second (add-enchants base type points
                                                     (if (pos? points) < >)
                                                     (if (pos? points) (constantly true) neg?)))
                               []))]
    (println "Enter desired points total: ")
    (if-let [points (util/&num)]
      (if-not (zero? points)
        (randomise-enchants points (mundane/&base)))
      []))
  (let [{:keys [base type]} (mundane/&base)]
    (util/rand-enabled (find-valid-enchants base type))))
