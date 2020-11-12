(ns campaign2.ring
  (:require
    [campaign2
     [util :as util]
     [state :refer [rings]]]))

(defn new []
  (->> @rings
       util/rand-enabled
       util/fill-randoms))
