(ns metav.api.spit
  (:require
    [clojure.spec.alpha :as s]
    [clojure.data.json :as json]
    [me.raynes.fs :as fs]
    [cljstache.core :as cs]
    [metav.api.common :as m-a-c]
    [metav.utils :as u]))



;;----------------------------------------------------------------------------------------------------------------------
;; Spit conf
;;----------------------------------------------------------------------------------------------------------------------
(def defaults-options
  #:metav.spit{:output-dir "resources"
               :namespace "meta"
               :formats #{:edn}})

(s/def :metav.spit/output-dir ::u/non-empty-str)
(s/def :metav.spit/namespace string?)
(s/def :metav.spit/formats (s/coll-of #{:clj :cljc :cljs :edn :json} :kind set?))
(s/def :metav.spit/template ::u/resource-path)
(s/def :metav.spit/rendering-output ::u/non-empty-str)

(s/def :metav.spit/options
  (s/keys :opt [:metav.spit/output-dir
                :metav.spit/namespace
                :metav.spit/formats
                :metav.spit/template
                :metav.spit/rendering-output]))

;;----------------------------------------------------------------------------------------------------------------------
;; Spit functionality
;;----------------------------------------------------------------------------------------------------------------------
(defn metafile! [output-dir namespace format]
  (fs/with-cwd output-dir
               (let [ns-file (fs/ns-path namespace)
                     parent (fs/parent ns-file)
                     name (fs/name ns-file)]
                 (fs/mkdirs parent)
                 (fs/file parent (str name "." format)))))


(defmulti spit-file! (fn [context] (::format context)))


(defmethod spit-file! :edn [context]
  (spit (::dest context)
        (pr-str (m-a-c/metadata-as-edn context))))


(defmethod spit-file! :json [context]
  (spit (::dest context)
        (json/write-str (m-a-c/metadata-as-edn context))))


(defmethod spit-file! :default [context];default are cljs,clj and cljc
  (spit (::dest context)
        (m-a-c/metadata-as-code context)))


(defn spit-files! [context]
  (let [{:metav/keys [working-dir]
         :metav.spit/keys [formats output-dir namespace]} context
        output-dir (str (fs/file working-dir output-dir))]

    (assert (u/ancestor? working-dir output-dir)
            "Spitted files must be inside the repo.")

    (mapv (fn [format]
            (let [dest (metafile! output-dir namespace (name format))]
              (spit-file! (assoc context
                           ::format format
                           ::dest dest))
              (str dest)))
          formats)))


(defn render! [context]
  (let [{:metav/keys [working-dir]
         :metav.spit/keys [template rendering-output]} context
        metadata (m-a-c/metadata-as-edn context)
        rendering-output (fs/with-cwd working-dir (fs/normalized rendering-output))]

    (assert (u/ancestor? working-dir rendering-output)
            "Rendered file must be inside the repo.")

    (spit rendering-output (cs/render-resource template metadata))
    (str rendering-output)))


(defn perform! [context]
  (s/assert :metav.spit/options context)
  (let [{:metav.spit/keys [template rendering-output]} context]
    (cond-> (spit-files! context)
            (and template rendering-output) (conj (render! context)))))