(ns campaign2.core
  (:require [campaign2
             [relic :as relic]
             [mundane :as mundane]
             [enchant :as enchant]
             [crafting :as crafting]
             [consumable :as consumable]
             [state :as state]]))

(defn start []
  {1  enchant/random-negative-enchanted
   2  mundane/new
   3  consumable/new

   13 crafting/new

   21 state/reload!
   22 relic/&level!})

(defn -main [& _]
  (start))
