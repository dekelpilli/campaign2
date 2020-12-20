(ns campaign2.crafting
  (:require
    [campaign2
     [util :as util]
     [enchant :as enchant]
     [mundane :as mundane]
     [state :refer [crafting-items]]]))

(def crafting-actions
  {:chaos       (fn []
                  (let [{:keys [base type]} (mundane/&base)]
                    {:base     base
                     :enchants (enchant/add-enchants base type
                                                     (- (rand-int 71) 20)
                                                     (constantly true))}))
   :destruction (fn []
                  (println "How many mods on the item?")
                  (let [num-mods (util/&num)]
                    (when (and num-mods (pos? num-mods))
                      (format "Remove mod number %s"
                              (rand-int (inc num-mods))))))
   :creation    (fn []
                  (let [{:keys [base type]} (mundane/&base)]
                    {:base     base
                     :enchants (enchant/add-enchants base type
                                                     (max 5
                                                          (+ (rand-int 21) (rand-int 21)))
                                                     (constantly true))}))
   :annexation  (fn []
                  (let [{:keys [base type]} (mundane/&base)]
                    (util/rand-enabled
                      (enchant/find-valid-enchants base type)))) ;TODO
   :exalted     (fn []
                  (let [{:keys [base type]} (mundane/&base)]
                    (->> (enchant/find-valid-enchants base type)
                         (filter #(pos? (:points % enchant/default-points)))
                         (util/rand-enabled))))})

(defn new []
  (util/get-multiple-items @crafting-items #(inc (rand-int 3))))

(defn &use []
  (let [opts (util/display-pairs (util/make-options crafting-actions))]
    (when-let [choice (some-> (util/&num)
                              opts
                              crafting-actions)]
      (choice))))
