(ns campaign2.relic
  (:require [campaign2
             [state :refer [*relics*]]
             [util :as util]]
            [clojure.tools.logging :as log]))

(defn- upgradeable? [{:keys [found? enabled? level]}]
  (and found? enabled? (<= level 10)))

(defn- &upgradeable []
  (let [upgradeable-relics (->> *relics*
                                (filter upgradeable?)
                                (map (fn [relic] [(:name relic) relic]))
                                (into {}))
        relic-options (->> upgradeable-relics
                           (keys)
                           (map-indexed (fn [i name] [i name]))
                           (into {}))]
    (when (not-empty relic-options)
      (log/infof "Relic options: %s" (clojure.pprint/write relic-options))
      (-> (util/&num)
          (relic-options)
          (upgradeable-relics)))))

(defn level-relic
  ([] (let [relic (&upgradeable)]
        (when relic (level-relic relic))))
  ([{:keys [level existing] :as relic}]
   ;TODO
   ))
