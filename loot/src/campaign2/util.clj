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

(defn display-multi-value [result]
  (table (if (sequential? result) result [result])))

(defn display-pairs
  ([p m] (println p) (display-pairs m))
  ([m] (table
         (->> m
              (into [])
              (sort)
              (concat [["Key" "Value"]])))))

(defn make-options [maps]
  (->> maps
       (keys)
       (map-indexed (fn [i option] [i option]))
       (into {})))

(defn &bool [default p]
  (let [opts {1 true 2 false}
        _ (if p (display-pairs p opts) (display-pairs opts))
        n (&num)]
    (opts n default)))

(defn rand-enabled [coll]
  (->> coll
       (filter #(:enabled? % true))
       (rand-nth)
       (#(dissoc % :enabled?))))

(defn fill-randoms [{:keys [randoms] :as item-modifier}]
  (if (not-empty randoms)
    (-> item-modifier
        (update :effect #(apply format % (map rand-nth randoms)))
        (dissoc :randoms))
    item-modifier))

;TODO: test with multiple metadata options
(defn get-multiple-items [coll f]
  (letfn [(multi-item-reducer
            ([default] default)
            ([v1 v2] (multi-item-reducer nil v1 v2))
            ([default v1 v2]
             (if-not (fn? v1)
               (multi-item-reducer (multi-item-reducer default v1) v2)
               (cond
                 (nil? v2) (multi-item-reducer nil default v1)
                 (= "disadvantage" v2) #(min (v1) (v1))
                 (= "advantage" v2) #(max (v1) (v1))
                 (.startsWith v2 "x") #(* (v1) (Long/parseLong (subs v2 1)))
                 :else (constantly (Long/parseLong v2))))))]
    (let [{:keys [metadata] :as item} (rand-enabled coll)
          randomiser (reduce multi-item-reducer f metadata)]
      {:amount ((or randomiser f))
       :item   item})))
