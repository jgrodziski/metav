(ns metav.api
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [clojure.tools.cli :as cli]
    [me.raynes.fs :as fs]

    [metav.version.protocols :as m-p]

    [metav.semver :as m-semver]
    [metav.maven :as m-maven]

    [metav.git :as m-git]
    [metav.metadata :as m-meta]
    [metav.repo :as m-repo]

    [metav.display :as m-display]
    [metav.spit :as m-spit]
    [metav.release :as m-release]

    [metav.display-cli :as m-display-cli]
    [metav.spit-cli :as m-spit-cli]
    [metav.release-cli :as m-release-cli]))


(s/def :metav/version-scheme #{:semver :maven})

(defn defaults-opts []
  (merge #:metav.display{:output-format nil}

         #:metav.spit{:output-dir "resources"
                      :namespace "meta"
                      :formats "edn"}

         #:metav.release{:without-sign false
                         :spit false
                         :without-push false}

         #:metav.cli{:verbose false}))

(defn last-dir [path]
  (last (fs/split path)))

;; get current commit id to compare on version bumps not to have 1.1.1 1.1.2 pointing o the same commit
(defn base-context []
  (let [root-repo (m-git/toplevel)
        working-dir (str (m-git/pwd))]
    #:metav{:version-scheme :semver

            :working-dir working-dir
            :root-repo root-repo
            :git-prefix (m-git/prefix working-dir)

            :mono-repo? (m-repo/monorepo? working-dir)
            :dedicated-repo? (m-repo/dedicated-repo? working-dir)}))


(defn prefix->name [pre]
  (let [name (string/replace pre "/" "-")]
    (subs name 0 (dec (count name)))))

(defn add-names [context]
  (let [{:metav/keys [root-repo git-prefix dedicated-repo?]} context
        project-name (last-dir root-repo)
        module-name (if dedicated-repo?
                      project-name
                      (prefix->name git-prefix))]

    (merge context
           #:metav{:project-name project-name
                   :module-name module-name})))

(defn add-version-prefix [context]
  (let [{:metav/keys [mono-repo? module-name]} context]
    (assoc context :metav/version-prefix (if mono-repo?
                                           (str module-name "-")
                                           "v"))))


(def version-scheme->builder
  {:semver m-semver/version
   :maven m-maven/version})


(defn add-version [context])

(defn make-context []
  (-> (base-context)
      (add-names)
      (add-version-prefix)))


(make-context)

(defn invocation-context
  ([]
   (invocation-context {}))
  ([opts]
   (let [opts (merge (defaults-opts) opts)])))


(comment
  (def default-cli
    (merge (cli/get-default-options m-display-cli/cli-options)
           (cli/get-default-options m-spit-cli/cli-options)
           (cli/get-default-options m-release-cli/cli-options)
           {:without-push true
            :without-sign true}))

  (m-meta/invocation-context default-cli)


  (defn to-map [v]
    {:tag (m-p/tag v)
     :distance (m-p/distance v)
     :sha (m-p/sha v)
     :version (str v)})

  (-> (m-meta/invocation-context default-cli)
      :version
      to-map)

  (m-release/execute! (m-meta/invocation-context default-cli) :patch))