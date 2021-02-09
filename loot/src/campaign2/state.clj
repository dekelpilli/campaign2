(ns campaign2.state
  (:require [clojure.pprint :as pprint]
            [clojure.java.io :as io])
  (:import (java.io PushbackReader)))

(def ^String path "loot/data/")

(defn load-data [type]
  (with-open [r (PushbackReader. (io/reader (str path type ".edn")))]
    (binding [*read-eval* false]
      (read r))))

(defn write-data! [a type]
  (with-open [writer (io/writer (str path type ".edn"))]
    (pprint/pprint @a writer)))

(def relics (atom nil))
(def prayer-paths (atom nil))
(def prayer-progressions (atom nil))
(def crafting-items (atom nil))
(def magic-items (atom nil))
(def weapons (atom nil))
(def armours (atom nil))
(def enchants (atom nil))
(def rings (atom nil))
(def consumables (atom nil))
(def miscreations (atom nil))
(def monsters (atom nil))
(def uniques (atom nil))

(defn reload! []
  (println "Loading...")
  (reset! relics (load-data "relic"))
  (reset! prayer-paths (load-data "prayer-path"))
  (reset! prayer-progressions (load-data "prayer-progress"))
  (reset! crafting-items (load-data "crafting-item"))
  (reset! magic-items (load-data "magic-item"))
  (reset! weapons (load-data "weapon"))
  (reset! armours (load-data "armour"))
  (reset! enchants (load-data "enchant"))
  (reset! rings (load-data "ring"))
  (reset! consumables (load-data "consumable"))
  (reset! miscreations (load-data "miscreation"))
  (reset! monsters (load-data "monster"))
  (reset! uniques (load-data "unique"))
  "Done")

(reload!)

(defn override-relics! [new-relics]
  (reset! relics new-relics)
  (write-data! relics "relic"))

(defn override-prayer-progress! [new-progress]
  (reset! prayer-progressions new-progress)
  (write-data! prayer-progressions "prayer-progress"))
