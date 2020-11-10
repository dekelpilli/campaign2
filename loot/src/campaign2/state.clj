(ns campaign2.state)

(def ^String path "loot/data/")

(defn- load-data [type]
  (->> (str path type ".edn")
       (slurp)
       (load-string)))

(defn- write-data! [a type]
  (clojure.pprint/pprint @a (clojure.java.io/writer (str path type ".edn"))))

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
  (println "Loading...")
  (reset! relics (load-data "relic"))
  (reset! prayer-paths (load-data "prayer-path"))
  (reset! prayer-progressions (load-data "prayer-progress"))
  (reset! crafting-items (load-data "crafting-item"))
  (reset! weapons (load-data "weapon"))
  (reset! armours (load-data "armour"))
  (reset! enchants (load-data "enchant"))
  (reset! consumables (load-data "consumable"))
  (reset! monsters (load-data "monster"))
  "Done")

(reload!)

(defn override-relics! [new-relics]
  (reset! relics new-relics)
  (write-data! relics "relic"))

(defn override-prayer-progress! [new-progress]
  (reset! prayer-progressions new-progress)
  (write-data! prayer-progressions "prayer-progress"))
