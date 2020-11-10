(ns campaign2.crafting
  (:require
    [campaign2
     [util :as util]
     [state :refer [crafting-items]]]))

(defn new []
  (util/get-multiple-items @crafting-items #(+ (rand-int 3) 1)))
