(ns campaign2.util
  (:require [clojure.edn :as edn]
            [table.core :as t]))

(defn &num []
  (let [input (-> (read-line)
                  (edn/read-string))]
    (if (number? input) input nil)))

(defn make-options [maps]
  (->> maps
       (keys)
       (map-indexed (fn [i option] [i option]))
       (into {})))

(defn display-result [result]
  (t/table (if (coll? result) (doall result) [result])))

(defn display-options
  ([p m] (println p) (display-options m))
  ([m] (t/table
         (->> m
              (into [])
              (sort)
              (concat [["Key" "Value"]])))))

(defn &bool [default]
  (let [opts {1 true 2 false}
        _ (display-options opts)
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
    (let [{:keys [metadata] :as item} (rand-nth coll)
          randomiser (reduce multi-item-reducer f metadata)]
      {:amount ((or randomiser f))
       :item   item})))
