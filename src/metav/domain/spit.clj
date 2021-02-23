(ns metav.domain.spit
  (:require
   [clojure.spec.alpha :as s]
   [clojure.data.json :as json]
   [me.raynes.fs :as fs]
   [cljstache.core :as cs]
   [metav.utils :as utils]
   [metav.domain.metadata :as metadata]
   [metav.domain.git :as git]
   [metav.domain.git-operations :as git-ops]
   [metav.domain.pom :as pom]))

;;----------------------------------------------------------------------------------------------------------------------
;; Spit conf
;;----------------------------------------------------------------------------------------------------------------------
(def defaults-options #:metav.spit{:output-dir "resources" :namespace "meta" :formats #{}})

(s/def :metav.spit/output-dir       ::utils/non-empty-str)
(s/def :metav.spit/namespace        string?)
(s/def :metav.spit/formats          (s/coll-of #{:clj :cljc :cljs :edn :json} :kind set?))
(s/def :metav.spit/template         ::utils/resource-path)
(s/def :metav.spit/rendering-output ::utils/non-empty-str)
(s/def :metav.spit/pom              boolean?)

(s/def :metav.spit/options (s/keys :opt [:metav.spit/output-dir
                                         :metav.spit/namespace
                                         :metav.spit/formats
                                         :metav.spit/template
                                         :metav.spit/rendering-output
                                         :metav.spit/pom]))

;;----------------------------------------------------------------------------------------------------------------------
;; Spit functionality
;;----------------------------------------------------------------------------------------------------------------------
(defn ensure-dir! [path]
  (when path
    (let [parent (-> path fs/normalized fs/parent)]
      (fs/mkdirs parent)
      path)))


(defn ensure-dest! [context]
  (ensure-dir! (::dest context)))

(defn run [f!]
  (fn [x]
    (f! x)
    x))

(defn add-extension [path ext]
  (let [path (fs/normalized path)]
    (fs/file (fs/parent path)
             (str (fs/name path) "." ext))))


(defn metafile [output-dir namespace format]
  (fs/with-cwd output-dir
    (-> namespace
        (fs/ns-path)
        (fs/normalized)
        (add-extension (name format)))))


(defmulti spit-file! (fn [context] (::format context)))


(defmethod spit-file! :edn [context]
  (ensure-dest! context)
  (when-let [dest (::dest context)]
    (spit dest (pr-str (metadata/metadata-as-edn context)))
    {:edn (str dest)}))


(defmethod spit-file! :json [context]
  (ensure-dest! context)
  (when-let [dest (::dest context)]
    (spit dest (json/write-str (metadata/metadata-as-edn context)))
    {:json (str dest)}))


(defmethod spit-file! :template [context]
  (ensure-dest! context)
  (when-let [dest (::dest context)]
    (spit dest (cs/render-resource (:metav.spit/template context)
                                   (metadata/metadata-as-edn context)))
    {:rendered-file (str dest)}))


(defmethod spit-file! :default [context];default are cljs,clj and cljc
  (ensure-dest! context)
  (when-let [dest (::dest context)]
      (spit dest (metadata/metadata-as-code context))
      {(::format context) dest}))


(defn data-spits
  ([context]
   (let [{:metav/keys [working-dir]
          :metav.spit/keys [formats output-dir namespace]} context]
     (data-spits context working-dir formats output-dir namespace)))
  ([context working-dir formats output-dir namespace]
   (let [output-dir (fs/file working-dir output-dir)]
     (utils/check (utils/ancestor? working-dir output-dir)
                  "Spitted files must be inside the repo.")
     (mapv (fn [format]
             (assoc context
                    ::dest (metafile output-dir namespace format)
                    ::format format)) formats))))

(defn template-spit [context]
  (let [{:metav/keys [working-dir]
         :metav.spit/keys [template rendering-output]} context]
    (when (and template rendering-output)
      (let [rendering-output (fs/with-cwd working-dir (fs/normalized rendering-output))]
        (utils/check (utils/ancestor? working-dir rendering-output)
                     "Rendered file must be inside the repo.")
        (assoc context ::dest rendering-output ::format :template)))))

(defn spit-files! [spits]
  (into [] (comp
            (map (run ensure-dest!))
            (map (run spit-file!))
            (map ::dest)
            (map str)) spits))

(defn sync-pom-and-commit! [context]
  (-> context
      pom/sync-pom!
      pom/git-add-pom!
      (git-ops/commit! "Synced and added pom.xml")))


(defn maybe-sync-pom! [context]
  (let [pom? (:metav.spit/pom context)]
    (cond-> context
      pom? sync-pom-and-commit!)))


(s/def ::spit!param (s/merge :metav/context :metav.spit/options))

(defn spit!
  "spit data and rendered template in files, return a map with keys {:data {:edn edn-file :json json-file ...} :rendered-template rendered-file}"
  ([context]
   (let [context               (utils/merge&validate context defaults-options ::spit!param)
         spitted-data          (into {} (map spit-file! (data-spits context)))
         spitted-rendered-file (spit-file! (template-spit context))
         spitted-pom-context   (when (:metav.spit/pom context) (pom/sync-pom! context))
         spitted-result        (cond-> {}
                                 (not-empty spitted-data) (assoc :data spitted-data)
                                 spitted-pom-context      (assoc :pom-file-path (:metav.maven.pom/pom-file-path spitted-pom-context))
                                 spitted-rendered-file    (assoc :template spitted-rendered-file))]
     (-> context
         (merge spitted-pom-context)
         (assoc :metav.spit/spitted spitted-result))))
  ([]
   (let [spitted-data  (into {} (map spit-file! (data-spits)))])))

(s/def :metav.spit/spitted map?)
(s/def ::git-add-spitted!-param (s/keys :req [:metav/working-dir :metav.spit/spitted]))

(defn git-add-spitted! [context]
  (utils/check-spec ::git-add-spitted!-param context)
  (let [{working-dir :metav/working-dir spitted :metav.spit/spitted} context
        spitted-files      (map str (filter identity (conj  (vals (:data spitted)) (:rendered-file (:template spitted)) (:pom-file-path spitted))))
        add-spitted-result (apply git/add! working-dir spitted-files)]
    (assoc context :metav.spit/add-spitted-result add-spitted-result)))
