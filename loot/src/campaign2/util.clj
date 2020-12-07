(ns campaign2.util
  (:require [table.core :as t]
            [clojure.edn :as edn]))

(defn ->num [s]
  (try
    (let [n (edn/read-string s)]
      (when (number? n) n))
    (catch Exception _)))

(defn &num []
  (->num (read-line)))

(defn- table [out]
  (binding [table.width/*width* (delay 9999)]
    (t/table out :style :unicode-3d)))

(defn display-multi-value [coll]
  (table (if (sequential? coll) coll [coll]))
  coll)

(defn display-pairs
  ([p m] (println p) (display-pairs m))
  ([m] (table
         (->> m
              (into [])
              (sort)
              (concat [["Key" "Value"]])))
   m))

(defn make-options [maps]
  (->> maps
       (keys)
       (sort)
       (map-indexed (fn [i option] [i option]))
       (into {})))

(defn rand-enabled [coll]
  (as-> coll $
       (filter #(:enabled? % true) $)
       (if (empty? $) nil (rand-nth $))
       (dissoc $ :enabled?)))

(defn fill-randoms [{:keys [randoms] :as item-modifier}]
  (if (not-empty randoms)
    (-> item-modifier
        (update :effect #(apply format % (map rand-nth randoms)))
        (dissoc :randoms))
    item-modifier))

(defn- disadv [f] #(min (f) (f)))
(defn- adv [f] #(max (f) (f)))
(defn- multi [f multiplier-str] #(* (f) (Long/parseLong (subs multiplier-str 1))))
(defn- static [const] (constantly (Long/parseLong const)))
(defn get-multiple-items [coll f]
  (letfn [(multi-item-reducer
            ([default] default)
            ([v1 v2] (multi-item-reducer nil v1 v2))
            ([default v1 v2]
             (if-not (fn? v1)
               (multi-item-reducer (multi-item-reducer default v1) v2)
               (cond
                 (nil? v2) (multi-item-reducer nil default v1)
                 (= "disadvantage" v2) (disadv v1)
                 (= "advantage" v2) (adv v1)
                 (.startsWith v2 "x") (multi v1 v2)
                 :else (static v2)))))]
    (let [{:keys [metadata] :as item} (rand-enabled coll)
          randomiser (reduce multi-item-reducer f metadata)]
      (assoc item :amount (randomiser)))))
