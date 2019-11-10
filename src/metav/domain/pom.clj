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
    [metav.utils :as utils])
  (:import [java.io Reader]
           [clojure.data.xml.node Element]))


(s/def :metav.maven/group-id string?)
(s/def :metav.maven.pom/name string?)

(s/def :metav.maven.pom/options
  (s/keys
    :opt [:metav.maven/group-id
          :metav.maven.pom/name]))

(s/def ::gen-pom!-param (s/merge :metav/context
                                 :metav.maven.pom/options))


(defn ctxt->group-id [context]
  (:metav/project-name context))


(defn ctxt->pom-name [context]
  (let [{:metav/keys [artefact-name version]
         group-id :metav.maven/group-id} context]
    (str group-id "/" artefact-name "-" version)))


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
(defn update-element [xml-root xml-path v]
  (xml-update xml-root
              xml-path
              (-> xml-path
                  last
                  (-> (vector v)
                      xml/sexp-as-element))))

(defn update-pom [xml-root context]
  (let [{group-id :metav.maven/group-id
         pom-name :metav.maven.pom/name
         :metav/keys [artefact-name version]} context]
    (-> xml-root
        (update-element [::pom/groupId]    group-id)
        (update-element [::pom/artifactId] artefact-name)
        (update-element [::pom/version]    (str version))
        (update-element [::pom/name] pom-name))))

;; rework of clojure.tools.deps.alpha.gen.pom/sync-pom
(defn update-pom! [context]
  (let [pom-file-path (ctxt->pom-path context)
        updated-pom (with-open [rdr (-> pom-file-path fs/file io/reader)]
                      (-> rdr
                          parse-xml
                          (update-pom context)))]
    (spit pom-file-path (xml/indent-str updated-pom))))


(defn sync-pom! [context]
  (let [{:metav/keys [project-deps working-dir]
         :as context} (-> context
                          (as-> context
                                (utils/ensure-key context :metav.maven/group-id (ctxt->group-id context))
                                (utils/ensure-key context :metav.maven.pom/name (ctxt->pom-name context)))
                          (->> (utils/check-spec :metav.maven.pom/options)))]

    (deps-pom/sync-pom project-deps (fs/file working-dir))
    (update-pom! context)
    context))
