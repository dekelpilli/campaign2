(ns campaign2.state
  (:require [clojure.tools.logging :as log]))

(def ^String path "loot/data/")

(defn- load-data [type]
  (-> (str path type ".edn")
      (load-file)))

(defn- write-data! [d type]
  (clojure.pprint/pprint d (clojure.java.io/writer (str path type ".edn"))))

(def ^:dynamic *relics* (load-data "relic"))
(def ^:dynamic *prayer-paths* (load-data "prayer-path"))
(def ^:dynamic *crafting-items* (load-data "crafting-item"))
(def ^:dynamic *weapons* (load-data "weapon"))
(def ^:dynamic *armours* (load-data "armour"))
(def ^:dynamic *enchants* (load-data "enchant"))
(def ^:dynamic *consumables* (load-data "consumable"))
(def ^:dynamic *monsters* (load-data "monster"))

(defn reload! []
  (log/infof "Loading...")
  (set! *relics* (load-data "relic"))
  (set! *prayer-paths* (load-data "prayer-path"))
  (set! *crafting-items* (load-data "crafting-item"))
  (set! *weapons* (load-data "weapon"))
  (set! *armours* (load-data "armour"))
  (set! *enchants* (load-data "enchant"))
  (set! *consumables* (load-data "consumable"))
  (set! *monsters* (load-data "monster")))

(defn override-relics! [relics]
  (set! *relics* relics)
  (write-data! *relics* "relic"))
