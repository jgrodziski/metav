(ns metav.domain.version.common
  (:require
    [metav.domain.version.protocols :as protocols]))

(def default-initial-subversions [0 1 0])


(defn bump-subversions [subversions level]
  (let [[major minor patch] subversions]
    (case level
      :major [(inc major) 0 0]
      :minor [major (inc minor) 0]
      :patch [major minor (inc patch)])))


(defn duplicating-version? [v level]
  (let [[_ minor patch] (protocols/subversions v)
        distance (protocols/distance v)
        same-patch? (= level :patch)
        same-minor? (and (= level :minor)
                         (= patch 0))
        same-major? (and (= level :major)
                         (= patch 0)
                         (= minor 0))]
    (and (= distance 0)
         (or same-patch?
             same-minor?
             same-major?))))


(defn going-backwards? [old-version new-version]
  (pos? (compare old-version new-version)))


(defn assert-bump? [old-version level new-version]
  (when (duplicating-version? old-version level)
    (throw (Exception. (str "Aborted released, bumping with level: " level
                            " would create version: " new-version " pointing to the same commit as version: " old-version "."))))
  (when (going-backwards? old-version new-version)
    (throw (Exception. (str "Can't bump version to an older one : " old-version " -> " new-version " isn't allowed.")))))

(defn bump [v level]
  (let [new-v (protocols/bump v level)]
    (assert-bump? v level new-v)
    new-v))
