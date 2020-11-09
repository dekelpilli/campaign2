(ns campaign2.enchant
  (:require [campaign2
             [state :refer [*enchants*]]
             [util :as util]
             [mundane :as mundane]]))

(defn find-valid-enchants [base-name type]
  (let [base (mundane/find-base base-name type)]
    ;todo (see get_valid_enchants_for_weapon/get_valid_enchants_for_armour)
    ))
