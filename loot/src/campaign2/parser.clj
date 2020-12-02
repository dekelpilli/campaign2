(ns campaign2.parser
  (:require [campaign2.state :as state]
            [campaign2.util :as u]
            [jsonista.core :as json])
  (:import (java.io File)
           (java.util ArrayList)))

(def ^String dir "loot/data/5et/")
(def mons* (ArrayList.))

(defn flatten-maps [k maps]
  (let [explode (fn [m key] (map #(assoc m key %) (m key)))]
    (-> (map #(explode % k) maps)
        (flatten))))

(defn find-cr [{:keys [cr]}]
  (u/->num (if (map? cr) (:cr cr) cr)))

(defn load-monsters []
  (let [files (->> dir
                   (File.)
                   (file-seq)
                   (filter (complement #(.isDirectory %))))
        mons (->> files
                  (map slurp)
                  (map #(json/read-value % json/keyword-keys-object-mapper))
                  (map :monster)
                  (flatten)
                  (filter (complement #(contains? % :_copy)))
                  (group-by find-cr)
                  (filter (fn [[k _]] k))
                  (map (fn [[k v]] [k
                                    (mapv #(select-keys % [:name :source :page]) v)]))
                  (into {})
                  (sort)
                  (into {}))]
    (state/write-data! (delay mons) "monster")))

