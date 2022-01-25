(ns campaign2.parser
  (:require [campaign2.state :as state]
            [campaign2.util :as u]
            [jsonista.core :as json]
            [clojure.string :as str])
  (:import (java.io File)
           (com.fasterxml.jackson.databind ObjectMapper)
           (java.util Date)
           (java.util.concurrent TimeUnit)))

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

(def ->school {"abjuration"     "A"
               "conjuration"    "C"
               "divination"     "D"
               "enchantment"    "E"
               "evocation"      "V"
               "illusion"       "I"
               "necromancy"     "N"
               "psionic"        "P"
               "transformation" "T" ;...
               "transmutation"  "T"})

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
                   (remove #(re-matches #"^(Chapter 10: Spellcasting|Adventurer’s Guide|0|[1-9][0-9]*)$" %))
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
                          new-idx)))))
            [spell lines] (split-at idx lines)]
        (if (or (empty? lines) (empty? spell))
          (conj spells spell)
          (if (zero? idx)
            (conj spells spell)
            (recur (conj spells spell) lines)))))))

;TODO fix empty string bug (e.g. cone of cold)
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

(defn merge-until-next-section [unparsed-lines section-end-pred]
  (let [first-line (first unparsed-lines)
        unparsed-lines (rest unparsed-lines)
        next-idx (reduce #(if (section-end-pred %2)
                            (reduced %1)
                            (inc %1))
                         0 unparsed-lines)]
    {:lines   (nthrest unparsed-lines next-idx)
     :content (str/join \newline (cons first-line (take next-idx unparsed-lines)))}))

(defn extract-pseudo-section-entries [content section-name]
  {:type "entries" :name section-name :entries [(-> content
                                                    (subs (+ 2 (count section-name)))
                                                    (str/escape {\newline \space}))]})

;https://www.jsonschemavalidator.net/

(defn extract-spell-sections [spell-lines]
  (loop [spell {:name    (first spell-lines)
                :page    497 ;spell page start, don't care about specifics
                :source  "LevelUpAdventurersGuideA5E"
                :entries []}
         section :level
         unparsed-lines (rest spell-lines)]
    (case section
      :level (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                     #(str/starts-with? % "Classes: "))
                   [level level-suffix] (str/split content #" \(" 2)
                   [school types] (str/split level-suffix #";" 2)
                   types (->sanitised-list types)] ;TODO put these somehwere?
               (recur
                 (assoc spell :level (->level level)
                              ;:tags types
                              :school (->school school))
                 :classes
                 lines))
      :classes (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                       #(str/starts-with? % "Casting Time: "))
                     classes (-> content
                                 (subs (count "Classes: "))
                                 (->sanitised-list))]
                 (recur (assoc spell :classes {:fromClassList (map (fn [class] {:name   (str/capitalize class)
                                                                                :source "LevelUpAdventurersGuideA5E"}) classes)})
                        :casting-time
                        lines))
      :casting-time (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                            #(or (str/starts-with? % "Range: ")
                                                                                 (str/starts-with? % "Target: ")
                                                                                 (str/starts-with? % "Area: ")
                                                                                 (str/starts-with? % "Components: ")))
                          [number raw-unit other] (-> (subs content (count "Casting Time: "))
                                                      (str/split #"\s" 3))
                          normalised-unit (->unit raw-unit)
                          amount (cond-> (u/->num number)
                                         (= "week" normalised-unit) (* 168))
                          unit (if (= "week" normalised-unit) "hour" normalised-unit)]
                      (recur
                        (-> spell
                            (assoc :time [{:number amount
                                           :unit   unit}])
                            (cond->
                              (and other
                                   (str/includes? (str/lower-case other) "ritual")) (assoc-in [:meta :ritual] true)))
                        :range
                        lines))
      :range (let [has-range? (str/starts-with? (first unparsed-lines) "Range: ")
                   [distance lines] (if has-range?
                                      (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                                              #(or (str/starts-with? % "Target: ")
                                                                                                   (str/starts-with? % "Area: ")
                                                                                                   (str/starts-with? % "Components: ")))
                                            range-sections (-> content
                                                               (subs (count "Range: "))
                                                               (str/lower-case)
                                                               (str/replace #"short|medium|long|\(|\)" "")
                                                               (str/trim)
                                                               (str/split #"\-| "))]
                                        [(if-let [special-type (#{"plane" "sight" "self"
                                                                  "touch" "unlimited" "special"} (first range-sections))]
                                           {:type (get {"same" "plane"} special-type special-type)
                                            ;:amount ;TODO check for amount
                                            }
                                           {:amount (u/->num (first range-sections))
                                            :type   (let [raw-type (second range-sections)]
                                                      (get {"foot" "feet" "mile" "miles"} raw-type raw-type))})
                                         lines])
                                      [{:type "special"} unparsed-lines])]
               (recur
                 (-> spell
                     (assoc-in [:range :type] "point") ;inaccurate, don't care
                     (assoc-in [:range :distance] distance))
                 :target
                 lines))
      :target (if (str/starts-with? (first unparsed-lines) "Target: ")
                (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                        #(or (str/starts-with? % "Area: ")
                                                                             (str/starts-with? % "Components: ")))
                      target (extract-pseudo-section-entries content "Target")]
                  (recur
                    (update spell :entries #(conj % target))
                    :area lines))
                (recur spell :area unparsed-lines))
      :area (if (str/starts-with? (first unparsed-lines) "Area: ")
              (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                      #(str/starts-with? % "Components: "))
                    area (extract-pseudo-section-entries content "Area")]
                (recur
                  ;TODO add areaTags(?)
                  (update spell :entries #(conj % area))
                  :components lines))
              (recur spell :components unparsed-lines))
      :components (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                          #(str/starts-with? % "Duration: "))
                        raw-components (-> content
                                           (subs (count "Components: "))
                                           (str/escape {\newline \space})
                                           (str/split #"," 3))
                        components (->> raw-components
                                        (map (comp #(str/split % #" " 2) str/trim))
                                        (into {} (map (fn [[component text]] [(str/lower-case component)
                                                                              (if text
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
                                                                        #(or (str/starts-with? % "Saving Throw: ")
                                                                             (re-matches #"^[A-Z].*" %)))
                      duration-text (-> content
                                        (subs (count "Duration: "))
                                        (str/escape {\newline \space})
                                        (str/trim))
                      duration-text-lower (str/lower-case duration-text) ;inconsistent casing in pdf
                      duration (cond
                                 (str/starts-with? duration-text-lower "until")
                                 {:type "permanent"
                                  :ends (cond-> []
                                                (str/includes? duration-text-lower "dispelled") (conj "dispel")
                                                (str/includes? duration-text-lower "trigger") (conj "trigger"))}
                                 (= duration-text-lower "instantaneous") {:type "instant"}
                                 (#{"varies" "special"} duration-text-lower) {:type "special"}
                                 :else (let [concentration? (str/starts-with? duration-text "Concentration ")
                                             [amount raw-unit] (-> duration-text
                                                                   (cond->
                                                                     concentration? (-> (subs (count "Concentration: "))
                                                                                        (str/escape {\( ""
                                                                                                     \) ""})))
                                                                   (str/trim)
                                                                   (str/split #" "))
                                             special? (= amount "special")]
                                         (cond-> {:type (if special? "special" "timed")}
                                                 (not special?) (assoc :duration {:type   (->unit raw-unit)
                                                                                  :amount (u/->num amount)})
                                                 concentration? (assoc :concentration true))))]
                  (recur (assoc spell :duration [duration])
                         :saving-throw
                         lines))
      :saving-throw (if (str/starts-with? (first unparsed-lines) "Saving Throw: ")
                      (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                              #(re-matches #"^[A-Z].*" %))
                            saving-throw (extract-pseudo-section-entries content "Saving Throw")
                            types (->> (str/split content #"\s")
                                       (map #{"strength" "dexterity" "constitution" "intelligence" "wisdom" "charisma"})
                                       (filter identity)
                                       (distinct))]
                        (recur
                          (-> spell
                              (update :entries #(conj % saving-throw))
                              (assoc :savingThrow types))
                          :entries lines))
                      (recur spell :entries unparsed-lines))
      :entries (let [{:keys [content lines]} (merge-until-next-section unparsed-lines
                                                                       #(or (str/starts-with? % "Cast at Higher Levels. ")
                                                                            (str/starts-with? % "Rare: ")))]
                 (recur
                   (update spell :entries #(into % (raw-content->entries content)))
                   :higher-levels
                   lines))
      :higher-levels (if (and
                           (seq unparsed-lines)
                           (str/starts-with? (first unparsed-lines)
                                             "Cast at Higher Levels. "))
                       (let [{:keys [content lines]} (merge-until-next-section unparsed-lines #(str/starts-with? % "Rare: "))
                             entries (-> content
                                         (subs (count "Cast at Higher Levels. "))
                                         (raw-content->entries))]
                         (recur
                           (assoc spell :entriesHigherLevel
                                        [{:type    "entries"
                                          :name    "Cast at Higher Levels"
                                          :entries entries}])
                           :rare
                           lines))
                       (recur spell :rare unparsed-lines))
      :rare (if (seq unparsed-lines)
              (let [{:keys [content lines]} (merge-until-next-section unparsed-lines #(str/starts-with? % "Rare: "))
                    entries (-> content
                                (subs (count "Rare: "))
                                (raw-content->entries))]
                (recur
                  (update spell :entries #(conj % {:type    "entries" :name "Rare"
                                                   :entries entries}))
                  :rare lines))
              spell))))

(defn convert-spells []
  (let [spell-lines (extract-spell-lines "data/a5e/spells/a5e-spells-touched-up.txt")]
    (map #(try
            (extract-spell-sections %)
            (catch Exception e
              (throw (ex-info (ex-cause e) {:original %} e)))) spell-lines)))

(defn write-spells []
  (let [now (.toSeconds TimeUnit/MILLISECONDS (inst-ms (Date.)))
        full {:_meta {:sources          [{:json         "LevelUpAdventurersGuideA5E"
                                          :abbreviation "A5E"
                                          :full         "Level Up: Adventurers Guide (A5E)"
                                          :url          "https://www.levelup5e.com/"
                                          :authors      ["Level Up"]
                                          :convertedBy  ["TODO DMs"]
                                          :version      "0.0.1"}]
                      :dateAdded        now
                      :dateLastModified now}
              :spell (convert-spells)}]
    (.writeValue ^ObjectMapper (json/object-mapper {:encode-key-fn true
                                                    :decode-key-fn true})
                 (File. "data/5et/generated/spells.json")
                 full)))
