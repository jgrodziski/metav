(ns metav.api
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [me.raynes.fs :as fs]

    [metav.version.protocols :as m-p]

    [metav.semver :as m-semver]
    [metav.maven :as m-maven]

    [metav.git :as m-git]

    [metav.version.common :as m-v-common]))



(defn pwd []
  (str (m-git/pwd)))


(defn defaults-opts []
  (merge #:metav{:use-full-name? false}

         #:metav.display{:output-format nil}

         #:metav.spit{:output-dir "resources"
                      :namespace "meta"
                      :formats "edn"}

         #:metav.release{:level :patch
                         :without-sign false
                         :spit false
                         :without-push false}

         #:metav.cli{:verbose false}))

(s/def :metav/version-scheme #{:semver :maven})
(s/def :metav/min-sha-length integer?)
(s/def :metav/use-full-name? boolean?)

(def default-metav-opts
  #:metav{:version-scheme :semver
          :min-sha-length 4
          :use-full-name? false})

(defn base-context
  [working-dir]
  #:metav{:working-dir working-dir
          :top-level (m-git/toplevel working-dir)
          :git-prefix (m-git/prefix working-dir)})


(def module-build-file "deps.edn")

(defn monorepo?
  "Does a single repo contains several modules? (dir with a build config like deps.edn in it).
  A monorepo is detected when the metav library is invoked correctly in a subdirectory of a git repo
  (so a deps.edn file is present in a subdirectory) "
  [context]
  (let [{:metav/keys [working-dir top-level git-prefix]} context]
    (boolean (or (and (= (fs/normalized (fs/file working-dir))
                         (fs/normalized (fs/file top-level)))
                      (> (count (fs/find-files working-dir #"deps.edn")) 1))
                 (and (not (nil? git-prefix))
                      (fs/file? (if working-dir
                                  (str working-dir "/" module-build-file)
                                  module-build-file)))))))

(defn dedicated-repo?
  "Does the current working directory contains a build system for a module (like deps.edn)"
  ([context]
    ;;assume the working dir contains a deps.edn
   (let [{:metav/keys [working-dir git-prefix]} context]
     (and (nil? git-prefix)
          (fs/file? (str working-dir "/" module-build-file))))))

(defn assoc-repo-tests [context]
  (merge context
         #:metav{:mono-repo? (monorepo? context)
                 :dedicated-repo? (dedicated-repo? context)}))


(defn git-prefix->module-name [pre]
  (let [name (string/replace pre "/" "-")]
    (subs name 0 (dec (count name)))))

(defn assoc-names [context]
  (let [{:metav/keys [top-level git-prefix dedicated-repo?]} context
        project-name (-> top-level fs/split last)
        module-name (if dedicated-repo?
                      project-name
                      (git-prefix->module-name git-prefix))]
    (merge context
           #:metav{:project-name project-name
                   :module-name module-name})))


(defn make-static-context [working-dir]
  (-> (base-context working-dir)
      (assoc-repo-tests)
      (assoc-names)))


(defn full-name [context]
  (let [{:metav/keys [mono-repo? project-name module-name]} context]
    (if mono-repo?
      (str project-name "-" module-name)
      module-name)))


(defn artefact-name [context]
  (if (get context :metav/use-full-name?)
    (:metav/full-name context)
    (:metav/module-name context)))


(defn version-prefix [context]
  (let [{:metav/keys [mono-repo? artefact-name]} context]
    (if mono-repo?
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


(defn assoc-computed [context k f]
  (assoc context k (f context)))


(defn make-computed-context [context opts]
  (-> context
      (merge default-metav-opts opts)
      (assoc-computed :metav/full-name full-name)
      (assoc-computed :metav/artefact-name artefact-name)
      (assoc-computed :metav/version-prefix version-prefix)
      (assoc-computed :metav/version version)))


(defn make-context
  ([]
   (make-context (pwd)))
  ([working-dir-or-opts]
   (if (string? working-dir-or-opts)
     (make-context working-dir-or-opts {})
     (make-context (pwd) working-dir-or-opts)))
  ([working-dir opts]
   (-> working-dir
       (make-static-context)
       (make-computed-context opts))))

(defn next-version [context level]
  (let [v (:metav/version context)
        subversions (m-p/subversions v)
        distance (m-p/distance v)]
    (println "subversions:" subversions)
    (println "distance:" distance))
  (m-p/bump (:metav/version context) level))

(comment

  (pwd)
  (defn print-identity [x]
    (println x)
    x)

  (def v1
    (-> (m-maven/version)
        (m-p/bump :minor)
        (m-p/bump :patch)
        (m-p/bump :alpha)
        (m-p/bump :alpha)
        (m-p/bump :alpha)
        (m-p/bump :beta)
        (m-p/bump :beta)))

  (def v2
    (-> (m-maven/version)
        (m-p/bump :minor)
        (m-p/bump :patch)
        (m-p/bump :alpha)
        (m-p/bump :alpha)
        (m-p/bump :alpha)))


  (neg? (compare v2 v1))

  (m-v-common/going-backwards v1 v2)
  (m-v-common/assert-bump? v1 :patch v2)



  (make-context)
  (make-context {:metav/module-name "toto"}))