(ns campaign2.encounter
  (:require [campaign2.util :as util]
            [campaign2.state :refer [positive-encounters]]
            [clojure.string :as str]
            [clojure.core.match :refer [match]]))

(def ^:private extra-loot-threshold 13)
(def ^:private races ["Aarakocra" "Aasimar" "Bugbear" "Centaur" "Changeling" "Dragonborn" "Dwarf" "Elf" "Firbolg"
                      "Genasi" "Gith" "Gnome" "Goblin" "Goliath" "Half-Elf" "Half-Orc" "Halfling" "Hobgoblin" "Human"
                      "Kalashtar" "Kenku" "Kobold" "Lizardfolk" "Loxodon" "Minotaur" "Orc" "Satyr" "Shifter" "Tabaxi"
                      "Tiefling" "Tortle" "Triton" "Vedalken" "Yuan-Ti Pureblood"])
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

(defn- calculate-loot [difficulty investigations]
  (let [avg (when investigations
              (as-> investigations $
                    (map #(Integer/parseInt %) $)
                    (/ (reduce + $) (count $))
                    (Math/round (double $))))]
    (let [excess (- avg extra-loot-threshold)
          remainder (mod (int excess) 3)
          base-loot (case difficulty
                      :easy ["2d8"]
                      :medium ["2d8" "1d12"]
                      :hard ["1d16"]
                      :deadly ["1d16" "1d16"])]
      (frequencies
        (if (>= avg extra-loot-threshold)
          (cond-> (concat base-loot (repeat (-> excess (/ 3) (int)) "1d16"))
                  (= remainder 1) (conj "1d12")
                  (= remainder 2) (conj "2d8"))
          base-loot)))))

(defn &rewards []
  (let [difficulties (util/display-pairs
                       (util/make-options [:easy :medium :hard :deadly]))
        difficulty (difficulties (util/&num))
        investigations (when difficulty
                         (println "List investigations: ")
                         (read-line))
        investigations (when investigations (str/split investigations #","))]
    (when difficulty
      {:xp   (case difficulty
               :easy (+ 6 (rand-int 2))
               :medium (+ 8 (rand-int 3))
               :hard (+ 11 (rand-int 3))
               :deadly (+ 13 (rand-int 4)))
       :loot (calculate-loot difficulty investigations)})))

(defn new-positive []
  {:race      (rand-nth races)
   :sex       (rand-nth sexes)
   :encounter (rand-nth @positive-encounters)})

(defn- new-room-dimensions []
  (vec (repeatedly 2 #(+ 4 (rand-int 6)))))

(defn- new-room-contents []
  ((rand-nth [#(format "Easy: %s mobs" (+ 2 (rand-int 5)))
              #(format "Medium: %s mobs" (+ 2 (rand-int 4)))
              #(format "Hard: %s mobs" (+ 4 (rand-int 3)))
              (constantly "Hard: 2 mobs")
              (constantly "Puzzle/trap")])))

(defn new-dungeon []
  (loop [remaining 3
         rooms []]
    (if (pos? remaining)
      (let [room (new-room-dimensions)
            [x y] room]
        (match room
               [9 9] (recur (inc remaining) (conj rooms {:x x :y y :contents (new-room-contents)}))
               [9 _] (recur remaining (conj rooms {:x x :y y :contents (new-room-contents)}))
               [_ 9] (recur remaining (conj rooms {:x x :y y :contents (new-room-contents)}))
               [_ _] (recur (dec remaining) (conj rooms {:x x :y y :contents (new-room-contents)}))))
      (as-> (new-room-dimensions) $
            (conj rooms {:x (first $) :y (second $) :contents "Boss"})))))