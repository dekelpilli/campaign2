(ns campaign2.enchant
  (:require
    [clojure.set :as s]
    [campaign2
     [state :refer [enchants]]
     [util :as util]
     [mundane :as mundane]]))

(def default-points 10)

;TODO: simplify, fix
(defn- compatible? [base enchant field]
  (let [not-field-val (->> field
                           (name)
                           (str "not-")
                           (keyword))
        item-field-val (base field)
        enchant-field (enchant field)]
    (if (coll? item-field-val)
      (and (s/intersection (set enchant-field) #{item-field-val})
           (empty? (s/intersection (set enchant-field) #{item-field-val})))
      (and
        (or (nil? enchant-field) (= enchant-field item-field-val))
        (or (nil? (enchant not-field-val)) (not (= enchant-field item-field-val)))))))

(defn- compatible-weapon? [{:keys [metadata category] :as base}
                           enchant]
  (and (or (empty? metadata)
           (contains? metadata "weapon")
           (and (= category "shield") (contains? metadata "armour")))
       (compatible? base enchant "traits")
       (compatible? base enchant "damage-types")
       (compatible? base enchant "proficiency")
       (compatible? base enchant "type")))

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

(defn- add-enchants [base type points-target points-comparator points-validator]
  (let [valid-enchants (->> (find-valid-enchants base type)
                            (filter #(points-validator (:points % default-points)))
                            (shuffle))
        sum (atom 0)
        enchants (->> valid-enchants
                      (filter #(and (points-comparator @sum points-target)
                                    (swap! sum (partial + (:points % default-points)))))
                      (map util/fill-randoms))]
    [base enchants]))

(defn- random-x-enchanted [points-target points-comparator points-validator]
  (let [type (rand-nth ["weapon" "armour"])
        base (case type
               "armour" (mundane/new-armour)
               "weapon" (mundane/new-weapon))]
    (add-enchants base type points-target points-comparator points-validator)))

(defn random-enchanted [points-target]
  (random-x-enchanted points-target < (constantly true)))

(defn random-positive-enchanted [points-target]
  (random-x-enchanted points-target < pos?))

(defn random-negative-enchanted []
  (random-x-enchanted (- 20 (rand-int -21)) > neg?))

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
