(ns campaign2.prayer
  (:require
    [campaign2
     [state :refer [prayer-paths prayer-progressions override-prayer-progress!]]
     [util :as util]]))

(defn new-stone []
  (->> prayer-paths
       (map :name)
       (rand-nth)))

(defn override-progress! [{:keys [player path] :as new-progression}]
  (override-prayer-progress! (mapv
                               #(if (and (= (:player %) player) (= (:path %) path)) new-progression %)
                               @prayer-progressions)))

(defn &progress! []
  (let [finished? #(contains? (:taken %) 10)
        unfinished-paths (filter #(not (finished? %)) @prayer-progressions)
        player-paths (group-by :player unfinished-paths)
        path-options (util/make-options player-paths)
        _ (util/display-options path-options)
        current-progress (->> (util/&num)
                              (path-options)
                              (player-paths)
                              (first))
        prayer-path (->> @prayer-paths
                         (filter #(= (:path current-progress) (:name %)))
                         (first))
        progress (reduce max (current-progress :taken))
        progress-index-options (->> (range progress 10)
                                    (take 2)
                                    (map (fn [i] [i (nth (prayer-path :levels) i)]))
                                    (map (fn [kv] [(inc (first kv)) (second kv)])) ;doesn't work? TODO - ensure first shows up as 1 and last as 10 and all matches
                                    (into {}))
        _ (util/display-options progress-index-options)
        new-latest-idx (dec (util/&num))
        valid (contains? progress-index-options new-latest-idx)]
    (when valid
      (override-progress!
        (update current-progress :taken #(conj % (+ new-latest-idx 1)))))))
