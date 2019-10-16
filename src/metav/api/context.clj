(ns metav.api.context
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [me.raynes.fs :as fs]

    [metav.version.semver :as m-semver]
    [metav.version.maven :as m-maven]
    [metav.git :as m-git]
    [metav.utils :as u]))



;; TODO make sure that in release, we actually release from repos, not the top level for instance.
(def default-options
  #:metav{:version-scheme :semver
          :min-sha-length 4
          :use-full-name? false})

(s/def :metav/working-dir fs/exists?)
(s/def :metav/version-scheme #{:semver :maven})
(s/def :metav/min-sha-length integer?)
(s/def :metav/use-full-name? boolean?)
(s/def :metav/module-name-override ::u/non-empty-str)


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
        top (m-git/toplevel working-dir)
        prefix  (m-git/prefix working-dir)]
    (when-not (string? top)
      (let [e (Exception. "Probably not working inside a git repository.")]
        (log/error e (str "git-top-level returned: " top " prefix returned:" (if (nil? prefix) "nil" prefix)))
        (throw e)))
    (assoc opts
      :metav/working-dir working-dir
      :metav/top-level top
      :metav/git-prefix  prefix)))


(def module-build-file "deps.edn")


(defn has-build-file? [working-dir]
  (let [build-file (fs/file working-dir module-build-file)]
    (fs/file? build-file)))


(defn assert-repo-in-order [context]
  (let [working-dir (:metav/working-dir context)]
    (when-not (m-git/any-commits? working-dir)
      (let [msg "No commits  found."
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


(defn assoc-names [context]
  (let [{:metav/keys [top-level git-prefix]} context
        project-name (fs/base-name top-level)
        module-name (if git-prefix
                      (git-prefix->module-name git-prefix)
                      project-name)]
    (merge context
           #:metav{:project-name project-name
                   :module-name module-name})))


(defn definitive-module-name [context]
  (let [{:metav/keys [module-name-override module-name]} context]
    (or module-name-override module-name)))


(defn full-name [context]
  (let [{:metav/keys [git-prefix project-name definitive-module-name]} context]
    (if git-prefix
      (str project-name "-" definitive-module-name)
      definitive-module-name)))


(defn artefact-name [context]
  (if (get context :metav/use-full-name?)
    (:metav/full-name context)
    (:metav/definitive-module-name context)))


(defn version-prefix [context]
  (let [{:metav/keys [git-prefix artefact-name]} context]
    (if git-prefix
      (str artefact-name "-")
      "v")))


(def version-scheme->builder
  {:semver m-semver/version
   :maven m-maven/version})


(defn version [context]
  (let [{:metav/keys [working-dir version-scheme version-prefix min-sha-length]} context
        make-version (get version-scheme->builder version-scheme)
        state (m-git/working-copy-description working-dir
                                              :prefix version-prefix
                                              :min-sha-length min-sha-length)]
    (when-not make-version
      (throw (Exception. (str "No version scheme " version-scheme " found! version scheme currently supported are: \"maven\" or \"semver\" "))))
    (when-not state
      (log/warn "No Git data available in directory " working-dir "! is it a git repository?
                 is there a proper .git dir? if so is there any commits? return default starting version"))
    (apply make-version state)))


(defn make-tag [context version]
  (str (:metav/version-prefix context) version))


(defn tag [context]
  (make-tag context (:metav/version context)))


(defn assoc-computed-keys [context]
  (-> context
      (u/assoc-computed :metav/definitive-module-name definitive-module-name)
      (u/assoc-computed :metav/full-name full-name)
      (u/assoc-computed :metav/artefact-name artefact-name)
      (u/assoc-computed :metav/version-prefix version-prefix)
      (u/assoc-computed :metav/version version)
      (u/assoc-computed :metav/tag tag)))


(s/check-asserts true)


(s/def :metav.context/param
  (s/keys :req [:metav/working-dir]))


(defn make-context [opts]
  (s/assert (s/and
              :metav.context/param
              :metav.context/options) opts)
  (->> opts
       (merge default-options)
       assoc-git-basics
       assert-repo-in-order
       assoc-names
       assoc-computed-keys))
