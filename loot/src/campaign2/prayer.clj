(ns campaign2.prayer
  (:require
    [campaign2
     [state :refer [prayer-paths prayer-progressions override-prayer-progress!]]
     [util :as util]]))

(defn new-stone []
  (->> prayer-paths
       (map :name)
       (rand-nth)))

(defn override-progress! [{:keys [character path] :as new-progression}]
  (override-prayer-progress! (mapv
                               #(if (and (= (:character %) character) (= (:path %) path)) new-progression %)
                               @prayer-progressions)))

(defn &progress! []
  (let [done? #(contains? (:taken %) 10)
        unfinished-paths (filter #(and (not (done? %)) (:enabled? % true)) @prayer-progressions)
        player-paths (group-by :character unfinished-paths)
        path-options (util/make-options player-paths)
        ;TODO - improve invalid input handling
        _ (util/display-pairs path-options)
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
                                    (map (fn [kv] [(inc (first kv)) (second kv)]))
                                    (into {}))
        _ (util/display-pairs progress-index-options)
        new-latest (util/&num)
        valid (contains? progress-index-options (dec new-latest))]
    (when valid
      (override-progress!
        (update current-progress :taken #(conj % new-latest))))))
