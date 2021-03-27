(ns campaign2.riddle
  (:require
    [campaign2
     [util :as util]
     [state :refer [riddles override-riddles!]]]))

(defn new! []
  (let [{:keys [riddle] :as randomised-riddle} (->> @riddles
                                                    (util/rand-enabled)
                                                    (util/fill-randoms))]
    (->> @riddles
         (map (fn [r] (if (= (:riddle r) riddle) randomised-riddle r)))
         (override-riddles!))
    randomised-riddle))
