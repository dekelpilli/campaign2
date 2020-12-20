(ns campaign2.crafting
  (:require
    [campaign2
     [util :as util]
     [enchant :as enchant]
     [state :refer [crafting-items]]]))

(def crafting-actions
  {:chaos       nil                                         ;TODO
   :destruction nil                                         ;TODO
   :creation    nil                                         ;TODO
   :annexation  nil                                         ;TODO
   :exalted     nil                                         ;TODO
   :mulligan    nil                                         ;TODO
   })

(defn new []
  (util/get-multiple-items @crafting-items #(inc (rand-int 3))))

(defn &use []
  (let [opts (util/display-pairs (util/make-options crafting-actions))]
    (when-let [choice (some-> (util/&num)
                              opts
                              crafting-actions)]
      (choice))))
