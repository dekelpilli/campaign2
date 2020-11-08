(ns campaign2.relic
  (:require [campaign2.state :refer :all]))

(defn choose-upgradeable-relic
  (->> *relics*
       (filter #(and (:found? %) (:enabled? %)))))
