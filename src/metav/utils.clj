(ns metav.utils
  (:require
    [clojure.spec.alpha :as s]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [me.raynes.fs :as fs]))


(defn pwd []
  (str fs/*cwd*))

(defn assoc-computed [context k f]
  (assoc context k (f context)))

;;----------------------------------------------------------------------------------------------------------------------
;; Spec functions
;;----------------------------------------------------------------------------------------------------------------------
(s/def ::non-empty-str (s/and string? (complement empty?)))

(defn resource? [p]
  (-> p io/resource fs/exists?))

(s/def ::resource-path (s/and ::non-empty-str
                              resource?))


(defn ancestor? [path possible-descendant]
  (string/starts-with? (str (fs/normalized possible-descendant))
                       (str (fs/normalized path))))
