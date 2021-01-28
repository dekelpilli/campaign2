(ns campaign2.encounter
  (:require [campaign2.util :as util]
            [clojure.string :as str]))

(def loot-values {:1d16 5
                  :2d8 4
                  :1d12 2})

(defn &randomise []
  (println "How many days?")
  (when-let [days (util/&num)]
    (->> (range 1 (inc days))
         (map (fn [i]
                [i
                 (when (util/occurred? 30)
                   (if (util/occurred? 20)
                     :positive ;todo: choose from positives
                     :random))]))
         (into {}))))

(defn &rewards []
  (let [difficulties (util/display-pairs
                       (util/make-options [:easy :medium :hard :deadly] {:sort? false})
                       {:sort? false})
        difficulty (difficulties (util/&num))
        type (when difficulty ((util/display-pairs
                                 (util/make-options [:random :noticeboard :main]
                                                    {:sort? false})
                                 {:sort? false})
                               (util/&num)))
        investigations (when type
                         (println "List investigations: ")
                         (read-line))
        avg (when investigations
              (as-> (str/split investigations #",") $
                    (map #(Integer/parseInt %) $)
                    (/ (reduce + $) (count $))))]
    (when avg
      (let [xp (case difficulty
                 :easy (+ 4 (rand-int 3))
                 :medium (+ 6 (rand-int 4))
                 :hard (+ 8 (rand-int 5))
                 :deadly (+ 12 (rand-int 6)))]
        {:xp   xp
         :loot avg ;TODO: use avg + difficulty/encounter type to get loot totals
         }))))
