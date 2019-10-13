(ns metav.api.context
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [me.raynes.fs :as fs]

    [metav.version.common :as m-v-common]

    [metav.semver :as m-semver]
    [metav.maven :as m-maven]

    [metav.git :as m-git]

    [metav.version.common :as m-v-common])
  (:import [java.util Date TimeZone]
           [java.text SimpleDateFormat]))


;; TODO make sure that in release, we actually release from repos, not the top level for instance.
(defn pwd []
  (str (m-git/pwd)))


(def defaults-opts
  (merge #:metav.spit{:output-dir "resources"
                      :namespace "meta"
                      :formats "edn"}

         #:metav.release{:level :patch
                         :without-sign false
                         :spit false
                         :without-push false}))

(s/def :metav/version-scheme #{:semver :maven})
(s/def :metav/min-sha-length integer?)
(s/def :metav/use-full-name? boolean?)
(s/def :metav/module-name-override #(or (nil? %) (string? %)))


(def default-metav-opts
  #:metav{:version-scheme :semver
          :min-sha-length 4
          :use-full-name? false
          :module-name-override nil})

(defn base-context
  [working-dir]
  (let [top (m-git/toplevel working-dir)
        prefix  (m-git/prefix working-dir)]
    (when-not (string? top)
      (let [e (Exception. "Probably not working inside a git repository.")]
        (log/error e (str "git-top-level returned: " top " prefix returned:" (if (nil? prefix) "nil" prefix)))
        (throw e)))
    #:metav{:working-dir working-dir
            :top-level top
            :git-prefix  prefix}))


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


(defn make-static-context [working-dir]
  (-> (base-context working-dir)
      (assert-repo-in-order)
      (assoc-names)))


(defn definitive-module-name [context]
  (let [{:metav/keys [module-name-override module-name]} context]
    (if module-name-override
      module-name-override
      module-name)))


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


(defn current-tag [context]
  (make-tag context (:metav/version context)))

(defn assoc-computed [context k f]
  (assoc context k (f context)))


(defn make-computed-context [context opts]
  (-> context
      (merge default-metav-opts opts)
      (assoc-computed :metav/definitive-module-name definitive-module-name)
      (assoc-computed :metav/full-name full-name)
      (assoc-computed :metav/artefact-name artefact-name)
      (assoc-computed :metav/version-prefix version-prefix)
      (assoc-computed :metav/version version)
      (assoc-computed :metav/tag current-tag)))


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


(defn new-version [context level]
  (m-v-common/bump (:metav/version context) level))


(defn iso-now []
  (let [tz (TimeZone/getTimeZone "UTC")
        df (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss'Z'")]
    (.setTimeZone df tz)
    (.format df (Date.))))


(defn metadata-as-edn [context]
  (let [{:metav/keys [artefact-name version tag git-prefix]} context]
    {:module-name artefact-name
     :version (str version)
     :tag tag
     :generated-at (iso-now)
     :path (if git-prefix git-prefix ".")}))


(defn metadata-as-code
  [context]
  (let [{:metav.spit/keys [namespace]} context
        {:keys [module-name path version tag generated-at]} (metadata-as-edn context)]
    (string/join "\n" [";; This code was automatically generated by the 'metav' library."
                       (str "(ns " namespace ")") ""
                       (format "(def module-name \"%s\")" module-name)
                       (format "(def path \"%s\")" path)
                       (format "(def version \"%s\")" version)
                       (format "(def tag \"%s\")" tag)
                       (format "(def generated-at \"%s\")" generated-at)
                       ""])))
