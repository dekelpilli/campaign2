(ns campaign2.mundane
  (:require [campaign2
             [state :refer [*armours* *weapons*]]
             [util :as util]]))

(defn find-base [base-name type]
  (first (filter #(= base-name (:name %))
                 ({:weapon *weapons* :armour *armours*} type))))
