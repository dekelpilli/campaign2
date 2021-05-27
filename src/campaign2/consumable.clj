(ns campaign2.consumable
  (:require
    [campaign2
     [util :as util]
     [state :refer [consumables]]]))

(defn new []
  (util/get-multiple-items @consumables #(inc (rand-int 4))))
