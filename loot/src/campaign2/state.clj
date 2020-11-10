(ns campaign2.state
  (:require [clojure.tools.logging :as log]))

(def ^String path "loot/data/")

(defn- load-data
  ([type] (load-data type nil))
  ([type default]
   (->> (str path type ".edn")
        (slurp)
        (load-string)
        (filter #(:enabled? % true))
        (map #(merge default %)))))

(defn- write-data! [d type]
  (clojure.pprint/pprint d (clojure.java.io/writer (str path type ".edn"))))

(def relics (atom nil))
(def prayer-paths (atom nil))
(def prayer-progressions (atom nil))
(def crafting-items (atom nil))
(def weapons (atom nil))
(def armours (atom nil))
(def enchants (atom nil))
(def consumables (atom nil))
(def monsters (atom nil))

(defn reload! []
  (log/infof "Loading...")
  (reset! relics (load-data "relic"))
  (reset! prayer-paths (load-data "prayer-path"))
  (reset! prayer-progressions (load-data "prayer-progress"))
  (reset! crafting-items (load-data "crafting-item"))
  (reset! weapons (load-data "weapon"))
  (reset! armours (load-data "armour"))
  (reset! enchants (load-data "enchant" {:points 10}))
  (reset! consumables (load-data "consumable"))
  (reset! monsters (load-data "monster"))
  "Done")

(reload!)

(defn override-relics! [new-relics]
  (reset! relics new-relics)
  (write-data! relics "relic"))

(defn override-prayer-progress! [new-progress]
  (reset! relics new-progress)
  (write-data! prayer-progressions "prayer-progress"))
