(ns campaign2.state
  (:require [clojure.tools.logging :as log]))

(def ^String path "loot/data/")

(defn- load-data [type]
  (->> (str path type ".edn")
       (load-file)
       (filter #(:enabled? % true))))

(defn- write-data! [d type]
  (clojure.pprint/pprint d (clojure.java.io/writer (str path type ".edn"))))

(def relics (atom (load-data "relic")))
(def prayer-paths (atom (load-data "prayer-path")))
(def crafting-items (atom (load-data "crafting-item")))
(def weapons (atom (load-data "weapon")))
(def armours (atom (load-data "armour")))
(def enchants (atom (load-data "enchant")))
(def consumables (atom (load-data "consumable")))
(def monsters (atom (load-data "monster")))

(defn reload! []
  (log/infof "Loading...")
  (swap! relics (constantly (load-data "relic")))
  (swap! prayer-paths (constantly (load-data "prayer-path")))
  (swap! crafting-items (constantly (load-data "crafting-item")))
  (swap! weapons (constantly (load-data "weapon")))
  (swap! armours (constantly (load-data "armour")))
  (swap! enchants (constantly (load-data "enchant")))
  (swap! consumables (constantly (load-data "consumable")))
  (swap! monsters (constantly (load-data "monster")))
  "Done")

(defn override-relics! [new-relics]
  (swap! relics (constantly new-relics))
  (write-data! relics "relic"))
