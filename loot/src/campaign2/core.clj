(ns campaign2.core
  (:require [campaign2
             [relic :as relic]
             [mundane :as mundane]
             [enchant :as enchant]
             [state :as state]]))


(defn start []
  {1 enchant/random-negative-enchanted
   2 mundane/new
   })

(defn -main [& _]
  (start))
