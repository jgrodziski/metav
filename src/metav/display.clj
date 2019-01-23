(ns metav.display
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [metav.maven];;require seems to be necessary when invoking dynamic resolve in the main function from the CLI
            [metav.semver]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.git :as git]
            [me.raynes.fs :as fs]))

(def ^:dynamic *scheme* "semver")

(defn pwd
  "return working dir of the JVM (cannot be changed once JVM is started)"
  []
  (.getCanonicalFile (clojure.java.io/file ".")))

(defn module-name
  "Determine the name for the project by analyzing the environment, path until the git root or folder name if just under the root"
  ([] (module-name nil))
  ([working-dir]
   (if (dedicated-repo? working-dir)
     (-> (git/toplevel working-dir)
         (fs/split)
         (last))
     ;;monorepo
     (clojure.string/replace (git/prefix working-dir) "/" "-"))))

(defn- version-scheme-fn [scheme]
  (ns-resolve (the-ns 'metav.display) (symbol (str "metav." scheme "/version"))))

(defn version
  "Determine the version for the project by dynamically interrogating the environment,
  you can choose the \"maven\" or \"semver\" version scheme"
  ([] (version nil :scheme "semver"));default value is semver
  ([working-dir & {:keys [scheme]
                   :or {scheme *scheme*}}]
   (let [version-scheme-fn (version-scheme-fn (clojure.string/lower-case scheme))
         prefix (if (monorepo? working-dir) (module-name working-dir) "v")
         state (git/working-copy-description working-dir :prefix prefix :min-sha-length 4)]
     (when-not version-scheme-fn
       (throw (Exception. (str "No version scheme " scheme " found! version scheme currently supported are: \"maven\" or \"semver\" "))))
     (when-not state
       (log/warn "No Git data available in directory "working-dir"! is it a git repository?
                  is there a proper .git dir? if so is there any commits? return default starting version"))
     (apply version-scheme-fn state))))

(defn artefact-name
  ([] (artefact-name nil))
  ([working-dir & {:keys [scheme]
                   :or {scheme *scheme*}}]
   (str (module-name working-dir) (version working-dir :scheme scheme))))

(def cli-options
  [["-vs" "--version-scheme SCHEME" "Version Scheme ('maven' or 'semver')"
    :default "semver"
    :validate #{"semver" "maven"}]])

(defn -main
  "Display the artefact name built from the module name + the current version obtained from the SCM environment"
  [& args]
  (let [working-dir (str (pwd))]
    ;(parse-opts args cli-options)
    (println (str (module-name working-dir) "-" (version working-dir)))
    (shutdown-agents)))
