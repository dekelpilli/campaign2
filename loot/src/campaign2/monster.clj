(ns campaign2.monster
  (:require [campaign2
             [state :refer [monsters]]]
            [campaign2.util :as util]))

(defn- weighted-rand-choice [m]
  (let [w (reductions #(+ % %2) (vals m))
        r (rand-int (last w))]
    (nth (keys m) (count (take-while #(<= % r) w)))))

(defn- of-cr [cr]
  (when-let [cr-monsters (@monsters cr)]
    (when-not (empty? cr-monsters) (rand-nth cr-monsters))))

(defn &new []
  (loop []
    (let [cr (util/&num)
          mon (of-cr cr)]
      (when (and cr (not (neg? cr)))
        (util/display-pairs mon)
        (recur)))))

(defn generate-amulet []
  (let [weighted-crs {0   5
                      1/8 5
                      1/4 5
                      1/2 10
                      1   25
                      2   25
                      3   15
                      4   10}
        cr (weighted-rand-choice weighted-crs)]
    (assoc (of-cr cr) :cr cr)))
