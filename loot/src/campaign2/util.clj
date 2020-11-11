(ns campaign2.util
  (:require [clojure.edn :as edn]
            [table.core :as t]))

(defn ->num [s]
  (try
    (let [n (edn/read-string s)]
      (when (number? n) n))
    (catch Exception _)))

(defn &num []
  (->num read-line))

(defn make-options [maps]
  (->> maps
       (keys)
       (map-indexed (fn [i option] [i option]))
       (into {})))

(defn- table [out]
  (binding [table.width/*width* (delay 9999)]
    (t/table out :style :unicode-3d)))

(defn display-multi-value [result]
  (table (if (coll? result) (doall result) [result])))

(defn display-pairs
  ([p m] (println p) (display-pairs m))
  ([m] (table
         (->> m
              (into [])
              (sort)
              (concat [["Key" "Value"]])))))

(defn &bool [default]
  (let [opts {1 true 2 false}
        _ (display-pairs opts)
        n (&num)]
    (opts n default)))

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
    (let [{:keys [metadata] :as item} (rand-nth (filter #(:enabled? % true) coll))
          randomiser (reduce multi-item-reducer f metadata)]
      {:amount ((or randomiser f))
       :item   item})))
