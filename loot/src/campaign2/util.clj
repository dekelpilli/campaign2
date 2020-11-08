(ns campaign2.util
  (:require [clojure.edn :as edn]))

(defn &num []
  (let [input (-> (read-line)
                  (edn/read-string))]
    (if (number? input) input nil)))
