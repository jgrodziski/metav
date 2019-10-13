(ns metav.api.spit
  (:require
    [clojure.spec.alpha :as s]
    [clojure.data.json :as json]
    [me.raynes.fs :as fs]
    [metav.api.context :as m-ctxt]))


;;----------------------------------------------------------------------------------------------------------------------
;; Spit conf
;;----------------------------------------------------------------------------------------------------------------------
(def defaults-spit-opts
  #:metav.spit{:output-dir "resources"
               :namespace "meta"
               :formats #{:edn}
               :template nil
               :rendering-output nil})

(s/def :metav.spit/output-dir string?)
(s/def :metav.spit/namespace string?)
(s/def :metav.spit/formats (s/and set? #(every? #{:clj :cljc :cljs :edn :json} %)))
(s/def :metav.spit/template #(or (nil? %) (string? %)))
(s/def :metav.spit/output-dir #(or (nil? %) (string? %)))

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
        (pr-str (m-ctxt/metadata-as-edn context))))

(defmethod spit-file! :json [context]
  (spit (::dest context)
        (json/write-str (m-ctxt/metadata-as-edn context))))

(defmethod spit-file! :default [context];default are cljs,clj and cljc
  (spit (::dest context)
        (m-ctxt/metadata-as-code context)))

(defn spit-files! [context]
  (let [{:metav/keys [working-dir]
         :metav.spit/keys [formats output-dir namespace]} context]
    (mapv (fn [format]
            (let [dest (metafile! (str (fs/file working-dir output-dir))
                                  namespace
                                  (name format))]
              (spit-file! (assoc context
                           ::format format
                           ::dest dest))
              (str dest)))
          formats)))


;; TODO: Implements the following!
(defn render! [])
(defn perform! [])