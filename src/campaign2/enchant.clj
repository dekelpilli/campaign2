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
        base-field-val (as-> (field base) $
                             (if (coll? $) (set $) #{$}))
        requisites (field enchant)
        incompatibles (not-field enchant)]
    (and (empty? (s/intersection base-field-val incompatibles))
         (or (empty? requisites)
             (seq (s/intersection base-field-val requisites))))))

(defn- compatible-weapon? [{:keys [range] :as base}
                           {:keys [ranged? metadata] :as enchant}]
  (and (or (empty? metadata)
           (contains? metadata "weapon"))
       (or (nil? ranged?) (= ranged? (boolean range)))
       (compatible? base enchant :traits)
       (compatible? base enchant :category)
       (compatible? base enchant :damage-types)
       (compatible? base enchant :proficiency)
       (compatible? base enchant :type)))

(defn- compatible-armour? [base
                           {:keys [metadata disadvantaged-stealth?] :as enchant}]
  (and (or (empty? metadata)
           (contains? metadata "armour"))
       (or (nil? disadvantaged-stealth?) (= disadvantaged-stealth? (:disadvantaged-stealth base)))
       (compatible? base enchant :type)))

(defn find-valid-enchants [base type]
  (let [enabled-enchants (filter #(:enabled? % true) @enchants)]
    (case type
      "weapon" (filter #(compatible-weapon? base %) enabled-enchants)
      "armour" (filter #(compatible-armour? base %) enabled-enchants))))

(defn add-enchants [base type points-target]
  (let [valid-enchants (->> (find-valid-enchants base type)
                            (shuffle))
        sum (atom 0)
        enchants (->> valid-enchants
                      (filter #(and (< @sum points-target)
                                    (swap! sum (partial + (:points % default-points)))))
                      (map util/fill-randoms))]
    [base enchants]))

(defn random-enchanted [points-target]
  (let [{:keys [base type]} (mundane/new)]
    (add-enchants base type points-target)))

(defn &add []
  (let [{:keys [base type]} (mundane/&base)]
    (when (and base type)
      (->> (find-valid-enchants base type)
           (util/rand-enabled)
           (util/fill-randoms)))))

(defn add-totalling [^long points]
  (if (pos-int? points)
    (if-let [{:keys [base type]} (mundane/&base)]
      (-> (add-enchants base type points)
          (second))
      [])
    []))
(defn &add-totalling
  []
  (println "Enter desired points total: ")
  (some-> (util/&num) (add-totalling)))
