(ns campaign2.miscreation
  (:require
    [campaign2
     [util :as util]
     [state :refer [miscreations]]]))

(defn new []
  (util/get-multiple-items @miscreations #(inc (rand-int 3))))
