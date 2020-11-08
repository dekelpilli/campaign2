(ns campaign2.state
  (:require [jsonista.core :as json]
            [clojure.java.io :as jio]
            [clojure.walk :as walk])
  (:import (java.util HashMap Map ArrayList List)
           (java.io File)))

(def ^String path "loot/data/")

(defn load-data [type]
  (-> (str path type ".edn")
      (load-file)
      (delay)))

(defn write-data [d n]
  (clojure.pprint/pprint d (clojure.java.io/writer n)))

(defn json->edn [type]
  (-> (str path type ".json")
      (slurp)
      (json/read-value json/keyword-keys-object-mapper)
      (write-data (str path type ".edn"))))

(defn do-sort []
  (let [fs (->> (file-seq (File. path))
                (map #(.getPath %))
                (filter #(.endsWith % ".edn"))
                (sort))]
    (doseq [f fs]
      (write-data (load-file f) f))))

(def ^:dynamic *relics* (load-data "relic"))
(def ^:dynamic *prayer-paths* (load-data "prayer-path"))
(def ^:dynamic *crafting-items* (load-data "crafting-item"))
(def ^:dynamic *weapons* (load-data "weapon"))
(def ^:dynamic *armours* (load-data "armour"))
(def ^:dynamic *enchants* (load-data "enchant"))
(def ^:dynamic *consumables* (load-data "consumable"))
(def ^:dynamic *monsters* (load-data "monster"))



