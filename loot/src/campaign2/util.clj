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
  ([m] (display-pairs m nil))
  ([m {:keys [sort? k v]
       :or   {sort? false
              k     "Key"
              v     "Value"}}]
   (table
     (as-> m $
           (into [] $)
           (if sort? (sort $) $)
           (concat [[k v]] $)))
   m))

(defn make-options
  ([coll] (make-options coll nil))
  ([coll {:keys [sort?] :or {sort? false}}]
   (as-> (if (map? coll) (keys coll) coll) $
         (if sort? (sort $) $)
         (map-indexed (fn [i option] [i option]) $)
         (into {} $))))

(defn rand-enabled [coll]
  (as-> coll $
        (filter #(:enabled? % true) $)
        (if (empty? $) nil (rand-nth $))
        (when $ (dissoc $ :enabled?))))

(defn fill-randoms [{:keys [randoms] :as item-modifier}]
  (if (not-empty randoms)
    (-> item-modifier
        (update :effect #(apply format % (map rand-nth randoms)))
        (dissoc :randoms))
    item-modifier))

(defn occurred? [likelihood-percentage]
  (< (rand-int 100) likelihood-percentage))

(defn- disadv [f] #(min (f) (f)))
(defn- adv [f] #(max (f) (f)))
(defn- multi [f multiplier-str] #(* (f) (Long/parseLong (subs multiplier-str 1))))
(defn- static [const] (constantly (Long/parseLong const)))
(defn get-multiple-items [coll f]
  (let [{:keys [metadata] :as item} (rand-enabled coll)
        randomiser (loop [randomiser f
                          coll metadata]
                     (let [modifier (first coll)
                           updated (cond
                                     (= "disadvantage" modifier) (disadv randomiser)
                                     (= "advantage" modifier) (adv randomiser)
                                     (.startsWith modifier "x") (multi randomiser modifier)
                                     :else (static modifier))
                           remaining (rest coll)]
                       (if (empty? remaining)
                         updated
                         (recur updated remaining))))]
    (-> item
        (assoc :amount (randomiser))
        (fill-randoms)
        (dissoc :metadata))))
