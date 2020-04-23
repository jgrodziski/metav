(ns metav.domain.pom
  (:require
   [clojure.data.xml :as xml]
   [clojure.data.xml.tree :as tree]
   [clojure.data.xml.event :as event]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.tools.deps.alpha.gen.pom :as deps-pom]
   [clojure.zip :as zip]
   [me.raynes.fs :as fs]
   [metav.domain.git :as git]
   [metav.utils :as utils])
  (:import [java.io Reader]
           [clojure.data.xml.node Element]))


(s/def :metav.maven/group-id string?)
(s/def :metav.maven.pom/name string?)
(s/def :metav.maven.pom/options (s/keys :opt [:metav.maven/group-id :metav.maven.pom/name]))


(defn ctxt->group-id [context]
  "com.atomist"
  #_(:metav/project-name context))


(defn ctxt->pom-name [context]
  (let [{:metav/keys [artefact-name version] group-id :metav.maven/group-id} context]
    (str group-id "/" artefact-name "-" version))
  (:metav/artefact-name context))


(defn ctxt->pom-path [context]
  (-> context :metav/working-dir (fs/file "pom.xml") str))

;;----------------------------------------------------------------------------------------------------------------------
;; taken from clojure.tools.deps.alpha.gen.pom
(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")


(defn- make-xml-element
  [{:keys [tag attrs] :as node} children]
  (with-meta
    (apply xml/element tag attrs children)
    (meta node)))


(defn- xml-update
  [root tag-path replace-node]
  (let [z (zip/zipper xml/element? :content make-xml-element root)]
    (zip/root
      (loop [[tag & more-tags :as tags] tag-path, parent z, child (zip/down z)]
        (if child
          (if (= tag (:tag (zip/node child)))
            (if (seq more-tags)
              (recur more-tags child (zip/down child))
              (zip/edit child (constantly replace-node)))
            (recur tags parent (zip/right child)))
          (zip/append-child parent replace-node))))))


(defn- parse-xml
  [^Reader rdr]
  (let [roots (tree/seq-tree event/event-element event/event-exit? event/event-node
                             (xml/event-seq rdr {:include-node? #{:element :characters :comment}
                                                 :skip-whitespace true}))]
    (first (filter #(instance? Element %) (first roots)))))
;;----------------------------------------------------------------------------------------------------------------------
(defn read-xml [path]
  (with-open [rdr (-> path fs/file io/reader)]
    (parse-xml rdr)))

(defn update-element [xml-root xml-path & x]
  (xml-update xml-root
              xml-path
              (-> (apply vector (last xml-path) x)
                  xml/sexp-as-element)))

(defn update-pom-identity [xml-root context]
  (let [{group-id :metav.maven/group-id
         pom-name :metav.maven.pom/name
         :metav/keys [artefact-name version]} context]
    (-> xml-root
        (update-element [::pom/groupId]    group-id)
        (update-element [::pom/artifactId] artefact-name)
        (update-element [::pom/version]    (str version))
        (update-element [::pom/name]       pom-name))))

(defn insert-pom-paths [xml-root context]
  (let [paths (get-in context [:metav/project-deps :paths])
        src-dir (first paths)
        other-dirs (rest paths)]
    (-> xml-root
        (update-element [::pom/build]
                        [::pom/sourceDirectory src-dir]
                        [::pom/resources
                         (map (fn [path] [::pom/resource [::pom/directory path]]) other-dirs)] ))))

;; rework of clojure.tools.deps.alpha.gen.pom/sync-pom
(defn update-pom! [context]
  (let [pom-file-path (ctxt->pom-path context)
        updated-pom   (-> pom-file-path
                          read-xml
                          (update-pom-identity context)
                          (insert-pom-paths context))]
    (spit pom-file-path (xml/indent-str updated-pom))
    (assoc context :metav.maven.pom/pom-file-path pom-file-path)))

(s/def ::sync-pom!-param (s/merge :metav/context :metav.maven.pom/options))

(defn sync-pom! [context]
  (let [{:metav/keys [project-deps working-dir] :as context} (-> context
                                                                 (as-> context
                                                                       (utils/ensure-key context :metav.maven/group-id (ctxt->group-id context))
                                                                       (utils/ensure-key context :metav.maven.pom/name (ctxt->pom-name context)))
                                                                 (->> (utils/check-spec ::sync-pom!-param)))]
    (deps-pom/sync-pom project-deps (fs/file working-dir))
    (update-pom! context)))

(s/def ::git-add-pom!-param (s/keys :req [:metav/working-dir :metav.maven.pom/pom-file-path]))

(defn git-add-pom! [context]
  (let [{working-dir   :metav/working-dir
         pom-file-path :metav.maven.pom/pom-file-path} context]
    (-> context
        (->> (utils/check-spec ::git-add-pom!-param))
        (assoc :metav.maven.pom/git-add-pom-result (git/add! working-dir pom-file-path)))))
