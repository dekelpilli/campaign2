(ns campaign2.ring
  (:require
    [campaign2
     [util :as util]
     [state :refer [rings]]]
    [clojure.string :as str])
  (:import (clojure.lang LineNumberingPushbackReader)))

(defn new []
  (->> @rings
       (util/rand-enabled)
       (util/fill-randoms)))

(defn new-synergy []
  (->> @rings
       (filter (fn [{:keys [name]}] (.startsWith name "The")))
       (util/rand-enabled)
       (util/fill-randoms)))

(defn &sacrifice []
  (println "Which rings are being sacrificed?")
  (let [ban-opts (->> @rings
                      (map :name)
                      (util/make-options)
                      (util/display-pairs))
        input (.readLine ^LineNumberingPushbackReader *in*)]
    (when-let [sacrificed (->> (str/split input #",")
                               (map #(Integer/parseInt %))
                               (map ban-opts)
                               set)]
      (->> @rings
           (filter (complement #(contains? sacrificed (% :name))))
           (shuffle)
           (take (if (contains? sacrificed "The Catalyst")
                   (* 2 (count sacrificed))
                   (count sacrificed)))
           (map util/fill-randoms)))))
