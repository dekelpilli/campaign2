(ns campaign2.util
  (:require [table.core :as t]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn weighted-rand-choice [m]
  (let [w (reductions #(+ % %2) (vals m))
        r (rand-int (last w))]
    (nth (keys m) (count (take-while #(<= % r) w)))))

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

(defn &choose
  ([coll] (&choose coll nil))
  ([coll opts]
   (let [options (display-pairs (make-options coll opts) opts)]
     (when-let [n (&num)]
       (as-> (options n) $
             (if (map? coll) (coll $) $))))))

(defn rand-enabled [coll]
  (as-> coll $
        (remove (comp false? :enabled?) $)
        (if (empty? $) nil (rand-nth $))
        (when $ (dissoc $ :enabled?))))

(defn fill-randoms [{:keys [randoms] :as item-modifier}]
  (if (seq randoms)
    (-> item-modifier
        (update :effect #(apply format % (map rand-nth randoms)))
        (dissoc :randoms))
    item-modifier))

(defn occurred? [likelihood-probability]
  (< (rand) likelihood-probability))

(defn- disadv [f] #(min (f) (f)))
(defn- adv [f] #(max (f) (f)))
(defn- multi [f multiplier-str] #(* (f) (Long/parseLong (subs multiplier-str 1))))
(defn- static [const] (constantly (Long/parseLong const)))
(defn get-multiple-items [coll f]
  (let [{:keys [metadata] :as item} (rand-enabled coll)
        randomiser (reduce #(cond
                              (= "disadvantage" %2) (disadv %1)
                              (= "advantage" %2) (adv %1)
                              (str/starts-with? %2 "x") (multi %1 %2)
                              :else (static %2))
                           f metadata)]
    (-> item
        (assoc :amount (randomiser))
        (fill-randoms)
        (dissoc :metadata))))
