(ns campaign2.encounter
  (:require [campaign2.util :as util]
            [campaign2.state :refer [positive-encounters]]
            [clojure.string :as str]))

(def ^:private extra-loot-threshold 13)
(def ^:private races ["Aarakocra" "Aasimar" "Bugbear" "Centaur" "Changeling" "Dragonborn" "Dwarf" "Elf" "Firbolg" "Genasi" "Gith" "Gnome" "Goblin" "Goliath" "Half-Elf" "Half-Orc" "Halfling" "Hobgoblin" "Human" "Kalashtar" "Kenku" "Kobold" "Lizardfolk" "Loxodon" "Minotaur" "Orc" "Satyr" "Shifter" "Tabaxi" "Tiefling" "Tortle" "Triton" "Vedalken" "Yuan-Ti Pureblood"])
(def ^:private sexes ["female" "male"])
(def ^:private had-random? (atom false))

(defn &randomise []
  (println "How many days?")
  (when-let [days (util/&num)]
    (->> (range 1 (inc days))
         (map (fn [i]
                [i
                 (when (util/occurred? (if @had-random? 15 30))
                   (if (util/occurred? 20)
                     :positive
                     (do
                       (reset! had-random? true)
                       :random)))]))
         (into (sorted-map)))))

(defn &rewards []
  (let [difficulties (util/display-pairs
                       (util/make-options [:easy :medium :hard :deadly]))
        difficulty (difficulties (util/&num))
        investigations (when difficulty
                         (println "List investigations: ")
                         (read-line))
        avg (when investigations
              (as-> (str/split investigations #",") $
                    (map #(Integer/parseInt %) $)
                    (/ (reduce + $) (count $))
                    (Math/round (double $))))]
    (when avg
      {:xp   (case difficulty
               :easy (+ 4 (rand-int 3))
               :medium (+ 6 (rand-int 4))
               :hard (+ 8 (rand-int 5))
               :deadly (+ 10 (rand-int 6)))
       :loot (when (and avg (>= avg extra-loot-threshold))
               (let [excess (- avg extra-loot-threshold)
                     remainder (mod (int excess) 3)]
                 (cond-> [(str (-> excess
                                   (/ 3)
                                   (int)
                                   (inc)) "x 1d16")]
                         (= remainder 1) (conj "1x 1d12")
                         (= remainder 2) (conj "1x 2d8"))))})))

(defn new-positive []
  {:race      (rand-nth races)
   :sex       (rand-nth sexes)
   :encounter (rand-nth @positive-encounters)})
