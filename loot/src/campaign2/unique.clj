(ns campaign2.unique
  (:require
    [campaign2
     [util :as util]
     [state :refer [uniques]]]))

(defn new []
  ;TODO format effects in more readable way
  (util/rand-enabled @uniques))
