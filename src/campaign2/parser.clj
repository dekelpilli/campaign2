(ns campaign2.parser
  (:require [campaign2.state :as state]
            [campaign2.util :as u]
            [jsonista.core :as json]
            [clojure.string :as str])
  (:import (java.io File)))

(def ^String dir "loot/data/5et/")

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
                   (remove #(.isDirectory %)))
        mons (->> files
                  (map slurp)
                  (map #(json/read-value % json/keyword-keys-object-mapper))
                  (map :monster)
                  (flatten)
                  (remove #(contains? % :_copy))
                  (group-by find-cr)
                  (filter first)
                  (map (fn [[k v]] [k
                                    (mapv #(select-keys % [:name :source :page :type]) v)]))
                  (into (sorted-map)))]
    (state/write-data! (delay mons) "monster")))

(defn idx-in-bounds? [idx coll] (<= idx (dec (count coll))))
(defn spell-level? [s]
  (or (str/starts-with? s "Cantrip (")
      (str/starts-with? s "1st-level (")
      (str/starts-with? s "2nd-level (")
      (str/starts-with? s "3rd-level (")
      (str/starts-with? s "4th-level (")
      (str/starts-with? s "5th-level (")
      (str/starts-with? s "6th-level (")
      (str/starts-with? s "7th-level (")
      (str/starts-with? s "8th-level (")
      (str/starts-with? s "9th-level (")))

(def ->level {"Cantrip"   0
              "1st-level" 1
              "2nd-level" 2
              "3rd-level" 3
              "4th-level" 4
              "5th-level" 5
              "6th-level" 6
              "7th-level" 7
              "8th-level" 8
              "9th-level" 9})

(def ->school {"abjuration"    "A"
               "conjuration"   "C"
               "divination"    "D"
               "enchantment"   "E"
               "evocation"     "V"
               "illusion"      "I"
               "necromancy"    "N"
               "psionic"       "P"
               "transmutation" "T"})

(defn sanitise
  ([coll]
   (sanitise coll nil))
  ([coll extra-keys]
   (let [cmap (into {\space ""
                     \,     ""} (map #(vector % "")) extra-keys)]
     (map #(str/escape % cmap) coll))))

(defn extract-spell-lines [^String file-name]
  (let [lines (->> (File. file-name)
                   (slurp)
                   (str/split-lines)
                   (remove #(re-matches #"^(0|[1-9][0-9]*)$" %))
                   (vec))]
    (loop [spells []
           lines lines]
      (let [idx (loop [spell-level-lines 0
                       idx 0]
                  (let [line (nth lines idx)]
                    (if (spell-level? line)
                      (if (zero? spell-level-lines)
                        (recur (inc spell-level-lines) (inc idx))
                        (dec idx))
                      (let [new-idx (inc idx)]
                        (if (idx-in-bounds? new-idx lines)
                          (recur spell-level-lines new-idx)
                          idx)))))
            [spell lines] (split-at idx lines)]
        (if (or (empty? lines) (empty? spell))
          spells
          (if (zero? idx)
            (conj spells spell)
            (recur (conj spells spell) lines)))))))

(defn merge-until-next-section [unparsed-lines next-prefix] ;TODO change next-prefix to next-pred to cater for optional sections
  (let [first-line (first unparsed-lines)
        unparsed-lines (rest unparsed-lines)
        next-idx (reduce #(if (str/starts-with? %2 next-prefix)
                            (reduced %1)
                            (inc %1))
                         0 unparsed-lines)]
    {:lines   (nthrest unparsed-lines next-idx)
     :content (reduce str first-line (take next-idx unparsed-lines))}))

(defn extract-spell-sections [spell-lines]
  (loop [spell {:name   (first spell-lines)
                :page   497 ;spell page start, don't care about specifics
                :source "A5E"}
         section :level
         unparsed-lines (rest spell-lines)]
    (case section
      :level (let [{:keys [content lines]} (merge-until-next-section unparsed-lines "Classes: ")
                   [level level-suffix] (str/split content #" \(" 2)
                   [school types] (str/split level-suffix #";" 2)
                   types (-> (str/split types #",")
                             (sanitise [\)]))]
               (recur
                 (-> spell
                     (assoc :level (->level level))
                     (assoc :tags types)
                     (assoc :school (->school school)))
                 :classes
                 lines))
      :classes (let [{:keys [content lines]} (merge-until-next-section unparsed-lines "Casting Time: ")
                     classes (-> content
                                 (subs (count "Classes: "))
                                 (str/split #",")
                                 (sanitise [\)]))]
                 (recur (assoc spell :classes {:fromClassList (map (fn [class] {:name   (str/capitalize class)
                                                                                :source "A5E"}) classes)})
                        :casting-time
                        lines))
      :casting-time (let [{:keys [content lines]} (merge-until-next-section unparsed-lines "Range: ")
                          [number unit other] (-> (subs content (count "Casting Time: "))
                                                  (str/split #" " 3))]
                      (recur
                        (-> spell
                            (assoc :time {:number (u/->num number)
                                          ;TODO normalise unit (remove plurals)
                                          :unit   unit})
                            (cond->
                              (and other
                                   (str/includes? (str/lower-case other) "ritual")) (assoc-in [:meta :ritual] true)))
                        :range
                        lines))
      :range (assoc spell :range \?))))

(defn convert-spells []
  (let [spell-lines (extract-spell-lines "data/a5e/spells/a/spells.txt")]
    (map extract-spell-sections spell-lines)))
