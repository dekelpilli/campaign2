(ns campaign2.unique
  (:require
    [campaign2
     [util :as util]
     [state :refer [uniques]]]
    [clojure.walk :as walk]))

(defn new []
  (let [{:keys [effects] :as unique} (util/rand-enabled @uniques)]
    (loop [[current & remaining] effects
           unique unique
           n 1]
      (if current
        (recur remaining
               (assoc unique (str n) (:effect (util/fill-randoms current)))
               (inc n))
        (as-> unique $
              (dissoc $ :effects)
              (walk/stringify-keys $)
              (into (sorted-map) $))))))
