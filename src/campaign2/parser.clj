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

(def level-str->level-int {"Cantrip"   0
                           "1st-level" 1
                           "2nd-level" 2
                           "3rd-level" 3
                           "4th-level" 4
                           "5th-level" 5
                           "6th-level" 6
                           "7th-level" 7
                           "8th-level" 8
                           "9th-level" 9})

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

(defn convert-spells []
  (let [spell-lines (extract-spell-lines "data/a5e/spells/a/spells.txt")]
    (map (fn [spell]
           (let [name (first spell)
                 [level & type-strs] (str/split (second spell) #" ") ;TODO handle multi-line level str
                 [school types] (str/split (str/join type-strs) #";")]
             ;TODO read classes/casting time/range/target/components/duration/save info
             ;TODO for spell description, remove newlines not preceded by period
             {:name   name
              :level  (level-str->level-int level)
              :school (str/join (rest school))
              :types  (when types
                        (map #(str/replace % #"\)" "")
                             (str/split types #",")))}))
         spell-lines)))
