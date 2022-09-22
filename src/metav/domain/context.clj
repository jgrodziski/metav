(ns metav.domain.context
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [clojure.tools.deps.alpha.specs :as deps-specs]
   [clojure.tools.deps.alpha :as deps]
   [me.raynes.fs :as fs]
   [metav.utils :as utils]
   [metav.domain.version.semver :as semver]
   [metav.domain.version.maven :as maven]
   [metav.domain.version.protocols :as ps]
   [metav.domain.name :as name]
   [metav.domain.git :as git]
   [metav.domain.tag :as tag]))



;; TODO make sure that in release, we actually release from repos, not the top level for instance.
(def default-options
  #:metav{:version-scheme :semver
          :min-sha-length 8
          :use-full-name? false})


(s/def :metav/working-dir          fs/exists?)
(s/def :metav/version-scheme       #{:semver :maven})
(s/def :metav/min-sha-length       integer?)
(s/def :metav/use-full-name?       boolean?)
(s/def :metav/module-name-override ::utils/non-empty-str)
(s/def :metav/project-deps         ::deps-specs/deps-map)


(s/def :metav.context/options
  (s/keys
    :opt [:metav/working-dir
          :metav/version-scheme
          :metav/min-sha-length
          :metav/use-full-name?
          :metav/module-name-override]))


(defn assoc-git-basics
  [opts]
  (let [working-dir (-> opts :metav/working-dir fs/normalized str)
        top-level   (git/toplevel working-dir)
        prefix      (git/prefix working-dir)]
    (when-not (string? top-level)
      (let [e (Exception. "Probably not working inside a git repository.")]
        (log/error e (str "git-top-level returned: " top-level " prefix returned:" (if (nil? prefix) "nil" prefix)))
        (throw e)))
    (assoc opts
      :metav/working-dir working-dir
      :metav/top-level   top-level
      :metav/git-prefix  prefix)))


(def module-build-file "deps.edn")

(defn has-build-file?
  "Checking that the working dir contains a `deps.edn` file."
  [working-dir]
  (let [build-file (fs/file working-dir module-build-file)]
    (fs/file? build-file)))

(defn assert-repo-has-commits-and-deps-edn?
  "Checks that the working dir has a build file and is in a repo
  which already has at least one commit."
  [context]
  (let [working-dir (:metav/working-dir context)]
    (when-not (git/any-commits? working-dir)
      (let [msg "No commits found."
            e (Exception. msg)]
        (log/fatal e msg)
        (throw e)))
    (when-not (has-build-file? working-dir)
      (let [msg "No build file detected."
            e (Exception. msg)]
        (log/fatal e msg)
        (throw e))))
  context)


(defn git-prefix->module-name [pre]
  (let [name (string/replace pre "/" "-")]
    (subs name 0 (dec (count name)))))


(defn assoc-names
  "Adds to the context basic names from git state:
  - `:metav/project-name`: from git rev-parse --show-toplevel
  - `:metav/module-name`: from git rev-parse --show-prefix"
  [context]
  (let [{:metav/keys [top-level git-prefix]} context
        project-name (fs/base-name top-level)
        module-name (if git-prefix
                      (git-prefix->module-name git-prefix)
                      project-name)]
    (merge context #:metav{:project-name project-name
                           :module-name module-name})))


(defn assoc-deps
  "Slurps the build file and adds it to the context under the key `:metav/project-deps`."
  [context]
  (let [working-dir (:metav/working-dir context)]
    (assoc context
      :metav/project-deps (-> working-dir
                              (fs/file module-build-file)
                              deps/slurp-deps))))



(def version-scheme->builder
  {:semver semver/version
   :maven  maven/version})

(defn version
  "Construct a version from a context and git state."
  ([{:metav/keys [working-dir version-scheme version-prefix min-sha-length] :as context}]
   (version working-dir version-scheme version-prefix min-sha-length))
  ([working-dir version-scheme version-prefix min-sha-length]
   (let [version-fn (get version-scheme->builder version-scheme)
         state      (git/working-copy-description working-dir :prefix version-prefix :min-sha-length min-sha-length)]
     (when-not version-fn
       (throw (Exception. (str "No version scheme " version-scheme " found! version scheme currently supported are: \"maven\" or \"semver\" "))))
     (when-not state
       (log/warn "No Git data available in directory " working-dir "! is it a git repository?
                  is there a proper .git dir? if so is there any commits? return default starting version"))
     (apply version-fn state))))

(defn format-tag [])



(defn assoc-computed-keys
  "Adds to a context all the computed info from a base context and git state."
  [context]
  (-> context
      (utils/assoc-computed :metav/definitive-module-name name/definitive-module-name)
      (utils/assoc-computed :metav/full-name              name/full-name)
      (utils/assoc-computed :metav/artefact-name          name/artefact-name)
      (utils/assoc-computed :metav/version-prefix         tag/version-prefix)
      (utils/assoc-computed :metav/version                version)
      (utils/assoc-computed :metav/tag                    tag/tag)))


(s/def :metav.context/required
  (s/keys :req [:metav/working-dir]))


(s/def ::make-context-param (s/merge
                              :metav.context/required
                              :metav.context/options))

(defn make-context [opts]
  (-> opts
      (utils/merge&validate default-options ::make-context-param)
      assoc-git-basics
      assert-repo-has-commits-and-deps-edn?
      assoc-names
      assoc-deps
      assoc-computed-keys
      (->> (into (sorted-map)))))


(s/def :metav/version (s/and #(satisfies? ps/Bumpable %)
                             #(satisfies? ps/SCMHosted %)))
(s/def :metav/artefact-name string?)
(s/def :metav/version-prefix string?)
(s/def :metav/tag string?)

(s/def :metav/context (s/keys :req [:metav/working-dir
                                    :metav/version
                                    :metav/artefact-name
                                    :metav/version-prefix
                                    :metav/tag]))
