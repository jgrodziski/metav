(ns metav.pom
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [clojure.tools.deps.alpha.gen.pom :as tda-pom]
   [clojure.tools.deps.alpha.reader :as tda-reader]
   [clojure.java.io :as io]
   [clojure.data.xml :as xml]
   [clojure.data.zip.xml :as zxml]
   [clojure.tools.deps.alpha.script.make-classpath :as makecp]
   [clojure.zip :as zip]
   [metav.git :as git]))

(defn- create-or-sync-pom!
  "Possibly unwisely reaches into some t.d.a internals to kick off pom creation or synchronization.
  Note that this ignores system/user deps.edn files and aliases. Ideally this is the same result as
  running `clojure -Spom`."
  [dir]
  ;; TODO: should probably include the system pom here, see tda-reader/clojure-env
  (let [deps (tda-reader/slurp-deps (io/file dir "deps.edn"))]
    (tda-pom/sync-pom deps (io/file dir))))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

;; cf https://github.com/clojure/clojure/blob/master/src/clj/clojure/zip.clj#L137-L147
(defn zip-top
  "Go up to the top of a zipper, like (zip/root) without the (zip/node) part"
  [loc]
  (loop [here loc]
    (if (zip/end? here)
      here
      (if-let [parent (zip/up here)]
        (recur parent)
        here))))

(defn empty-scm-tag []
  (-> [::pom/scm
       [::pom/url]
       [::pom/tag]
       [::pom/connection]
       [::pom/developerConnection]]
      xml/sexp-as-element))

(defn create-scm-tag-if-missing
  [pom-zip]
  (let [top (zip-top pom-zip)]
    (if (zxml/xml1-> top ::pom/project ::pom/scm)
      (do
        (log/debug "Found existing <scm> tag in pom.xml")
        top)
      (do
        (log/debug "No <scm> tag found in pom.xml, creating one")
        (-> top (zip/append-child (empty-scm-tag)) zip-top)))))

(defn to-maven-scheme [git-url]
  (when git-url (str "scm:git:" git-url)))

(defn replace-paths [pom-zip path-map]
  (reduce
    (fn [loc [loc-path replacement]]
      (let [top     (zip-top loc)
            human   (string/join "/" (map name loc-path))
            found   (apply zxml/xml1-> (into [top] loc-path))
            val-loc (some-> found zip/down)
            current (some-> val-loc zip/node)]
        (cond
          (nil? replacement)
          loc ; skip over nil keys
          (and (some? val-loc) (= current replacement))
          (do
            (log/debugf "Value at path %s already has value %s, skipping" human replacement)
            loc)
          (some? val-loc)
          (do
            (log/debugf "Updating path %s from %s to %s" human current replacement)
            (-> val-loc (zip/replace replacement) zip-top))
          (some? found)
          (do
            (log/debugf "Creating new value in path %s as %s" human replacement)
            (-> found (zip/insert-child replacement) zip-top))
          :else
          (do
            (log/debugf "Path %s not found in data, skipping" human)
            loc))))
    pom-zip
    path-map))

(defn update-pom!
  "Open the pom.xml file in the current directory, replace its values from the data found in
  git and in the command-line arguments, and write it back to pom.xml. Like `clojure -Spom`,
  this operation should leave any existing data intact."
  ([version options]
   (update-pom! (io/file ".") version options))
  ([dir version {:keys [artifact-id group-id scm-url] :as options}]
   (let [pom-file                   (io/file dir "pom.xml")
         [base distance sha dirty?] (git/working-copy-description)
         git-url                    (to-maven-scheme (git/remote-url))
         pom                        (with-open [rdr (io/reader pom-file)]
                                      (-> rdr
                                          xml/parse
                                          zip/xml-zip
                                          create-scm-tag-if-missing
                                          (replace-paths {[::pom/project ::pom/artifactId]                    artifact-id
                                                          [::pom/project ::pom/groupId]                       group-id
                                                          [::pom/project ::pom/version]                       version
                                                          [::pom/project ::pom/scm ::pom/connection]          git-url
                                                          [::pom/project ::pom/scm ::pom/developerConnection] git-url
                                                          [::pom/project ::pom/scm ::pom/tag]                 sha
                                                          [::pom/project ::pom/scm ::pom/url]                 scm-url})
                                          zip/root))
         content                    (xml/indent-str pom)]
     (spit pom-file content)
     (log/info (format "Updated pom.xml to version %s%s%s%s"
                       version
                       (if group-id (format ", groupId %s" group-id) "")
                       (if artifact-id (format ", artifactId %s" artifact-id) "")
                       (if scm-url (format ", scm/url %s" scm-url) ""))))))

;; todo, could add an option to remove pom.xml prior to -Spom
(defn generate!
  "Use tools.deps to create or update a pom.xml, and then post-process it to plug in some values."
  ([{:keys [working-dir module-name scm-url] :as invocation-context} version]
   (let [ver-str (.toString version)
         options (merge {:group-id module-name :artifact-id module-name} invocation-context)]
     (log/info (format "Generating pom.xml in dir %s for %s..." working-dir ver-str))
     (create-or-sync-pom! working-dir)
     (update-pom! working-dir ver-str options)
     (.toString (io/file working-dir "pom.xml")))))


(comment
  ;; testing
  (defn xml-parse [string] (-> string .getBytes java.io.ByteArrayInputStream. xml/parse zip/xml-zip))
  (defn xml-tree [string] (-> string xml-parse zip/xml-zip))

  (def main-src "<root><any>foo</any>bar</root>")
  (def extra (xml/sexp-as-element [:scm
                                   [:url]
                                   [:tag]
                                   [:connection]
                                   [:developerConnection]]))

  (def loc (xml-tree main-src))

  (defn hmm [loc] (-> loc zip/node xml/indent-str println))

  (defn pom []
    (with-open [rdr (io/reader "pom.xml")]
      (-> rdr
          xml/parse
          zip/xml-zip))))
