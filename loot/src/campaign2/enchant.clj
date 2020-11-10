(ns campaign2.enchant
  (:require
    [clojure.set :as s]
    [campaign2
     [state :refer [enchants]]
     [util :as util]
     [mundane :as mundane]]
    [clojure.tools.logging :as log]))

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
  (case type
    "weapon" (filter #(compatible-weapon? base %) @enchants)
    "armour" (filter #(compatible-armour? base %) @enchants)))

(defn random-negative-enchanted []
  (let [floor -25
        type (rand-nth ["weapon" "armour"])
        base (case type
               "armour" (mundane/new-armour)
               "weapon" (mundane/new-weapon))
        valid-enchants (->> (find-valid-enchants base type)
                            (filter #(neg? (:points %)))
                            (shuffle))
        sum* (atom 0)]
    (log/infof "Base: %s\newlineEncahnts: %s"
               base
               (filter #(and (> @sum* floor) (swap! sum* (partial + (:points %)))) valid-enchants))))
