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

(defn ->unit [unit]
  (let [unit (str/replace unit #"\s|,|\(|\)" "")]
    (get {"actions"   "action"
          "reactions" "reaction"
          "rounds"    "round"
          "minutes"   "minute"
          "hours"     "hour"
          "days"      "day"
          "weeks"     "week"
          "months"    "month"
          "years"     "year"} unit unit)))

(defn ->sanitised-list [s]
  (->> (str/split s #",")
       (map #(str/escape % {\space   ""
                            \newline ""
                            \,       ""
                            \)       ""}))))

(defn extract-spell-lines [^String file-name]
  (let [lines (->> (File. file-name)
                   (slurp)
                   (str/split-lines)
                   (remove #(re-matches #"^(Adventurer’s Guide|0|[1-9][0-9]*)$" %))
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

(defn raw-content->entries [content]
  (cond-> (loop [entry-lines []
                 entries []
                 [current & remaining] (str/split-lines content)]
            (if current
              (let [current (str/trim current)]
                (if (re-matches #"[^.]+[\.:]" current)
                  (recur []
                         (conj entries (conj entry-lines current))
                         remaining)
                  (recur (conj entry-lines current)
                         entries
                         remaining)))
              (->> (cond-> entries
                           (seq entry-lines) (conj entries entry-lines))
                   (mapv #(str/join \space %)))))
          (str/includes? content "TABLE") (conj "<<<ADD TABLE MANUALLY>>>")
          (str/includes? content "•") (conj "<<<ADD LIST MANUALLY>>>")))

(defn merge-until-next-section [unparsed-lines next-pred]
  (let [first-line (first unparsed-lines)
        unparsed-lines (rest unparsed-lines)
        next-idx (reduce #(if (next-pred %2)
                            (reduced %1)
                            (inc %1))
                         0 unparsed-lines)]
    {:lines   (nthrest unparsed-lines next-idx)
     :content (str/join \newline (cons first-line (take next-idx unparsed-lines)))}))

(defn extract-spell-sections [spell-lines]
  (loop [spell {:name   (first spell-lines)
                :page   497 ;spell page start, don't care about specifics
                :source "A5E"}
         section :level
         unparsed-lines (rest spell-lines)]
    (case section
      :level (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                     #(str/starts-with? % "Classes: "))
                   [level level-suffix] (str/split content #" \(" 2)
                   [school types] (str/split level-suffix #";" 2)
                   types (->sanitised-list types)]
               (recur
                 (assoc spell :level (->level level)
                              :tags types
                              :school (->school school))
                 :classes
                 lines))
      :classes (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                       #(str/starts-with? % "Casting Time: "))
                     classes (-> content
                                 (subs (count "Classes: "))
                                 (->sanitised-list))]
                 (recur (assoc spell :classes {:fromClassList (map (fn [class] {:name   (str/capitalize class)
                                                                                :source "A5E"}) classes)})
                        :casting-time
                        lines))
      :casting-time (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                            #(str/starts-with? % "Range: "))
                          [number unit other] (-> (subs content (count "Casting Time: "))
                                                  (str/split #"\s" 3))]
                      (recur
                        (-> spell
                            (assoc :time {:number (u/->num number)
                                          :unit   (->unit unit)})
                            (cond->
                              (and other
                                   (str/includes? (str/lower-case other) "ritual")) (assoc-in [:meta :ritual] true)))
                        :range
                        lines))
      :range (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                     #(or (str/starts-with? % "Area: ")
                                                                          (str/starts-with? % "Target: ")
                                                                          (str/starts-with? % "Components: ")))
                   range-str (subs content (count "Range: "))
                   distance (if (#{"Self" "Touch"} range-str)
                              {:type (str/lower-case range-str)}
                              (as-> range-str $
                                    (str/replace $ #"Short|Medium|Long|\(|\)" "")
                                    (str/trim $)
                                    (str/split $ #" ")
                                    {:amount (u/->num (first $))
                                     :type   (second $)}))
                   lines (if (str/starts-with? (first lines) "Components: ")
                           lines
                           (:lines (merge-until-next-section unparsed-lines #(str/starts-with? % "Components: "))))]
               (recur
                 (-> spell
                     (assoc-in [:range :type] "point") ;inaccurate, don't care
                     (assoc :distance distance))
                 :components
                 lines))
      :components (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                          #(str/starts-with? % "Duration: "))
                        raw-components (-> content
                                           (subs (count "Components: "))
                                           (str/escape {\newline \space})
                                           (str/split #"," 3))
                        components (->> raw-components
                                        (map (comp #(str/split % #" " 2) str/trim))
                                        (into {} (map (fn [[component text]] [component (if text
                                                                                          {:text (-> text
                                                                                                     (str/escape {\( ""
                                                                                                                  \) ""})
                                                                                                     (str/trim))}
                                                                                          true)]))))]
                    (recur
                      (assoc spell :components components)
                      :duration
                      lines))
      :duration (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                        #(re-matches #"^[A-Z].*" %))
                      duration-text (-> content
                                        (subs (count "Duration: "))
                                        (str/escape {\newline \space})
                                        (str/trim))
                      duration (cond
                                 (= duration-text "Until dispelled or the target is broken") {:type "permanent"
                                                                                              :ends ["dispel"]}
                                 (= duration-text "Instantaneous") {:type "instant"}
                                 (= duration-text "Varies") {:type "varies"}
                                 (= duration-text "Special") {:type "special"}
                                 :else (let [concentration? (str/starts-with? duration-text "Concentration ")
                                             [amount raw-unit] (-> duration-text
                                                                   (cond->
                                                                     concentration? (-> (subs (count "Concentration: "))
                                                                                        (str/escape {\( ""
                                                                                                     \) ""})))
                                                                   (str/trim)
                                                                   (str/split #" "))]
                                         (cond-> {:type     "timed"
                                                  :duration {:type   (->unit raw-unit)
                                                             :amount (u/->num amount)}}
                                                 concentration? (assoc :concatenation true))))]
                  (recur (assoc spell :duration duration)
                         :entries
                         lines))
      :entries (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                       #(str/starts-with? % "Cast at Higher Levels. "))]
                 (recur
                   (assoc spell :entries (raw-content->entries content))
                   :higher-levels
                   lines))
      :higher-levels (if (and
                           (seq unparsed-lines)
                           (str/starts-with? (first unparsed-lines)
                                             "Cast at Higher Levels. "))
                       (let [{:keys [content lines]} (merge-until-next-section unparsed-lines #(str/starts-with? % "Rare: "))
                             entries (raw-content->entries content)]
                         (recur
                           (assoc spell :entriesHighLevel
                                        {:type    "entries"
                                         :name    "Cast at Higher Levels"
                                         :entries entries})
                           :rare
                           lines))
                       spell)
      :rare (if (seq unparsed-lines)
              (let [entries (-> unparsed-lines
                                (merge-until-next-section (constantly false))
                                (:content)
                                (raw-content->entries))]
                (update spell :entries #(into % entries)))
              spell))))

(defn convert-spells []
  (let [spell-lines (extract-spell-lines "data/a5e/spells/a/spells.txt")]
    (map extract-spell-sections spell-lines)))
