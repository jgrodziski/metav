(ns metav.display
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [metav.maven];;require seems to be necessary when invoking dynamic resolve in the main function from the CLI
            [metav.semver]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.git :as git]
            [me.raynes.fs :as fs]))

(defn- version-scheme-fn [scheme]
  (ns-resolve (the-ns 'metav.display) (symbol (str "metav." scheme "/version"))))

(defn version
  "Determine the version for the project by dynamically interrogating the environment, we can choose the Maven or SemVer version scheme"
  ([] (version "semver"));default value is semver
  ([scheme] (version scheme nil))
  ([scheme repo-dir]
   (let [version-scheme-fn (version-scheme-fn (clojure.string/lower-case scheme))
         state (git/working-copy-description repo-dir)]
     (when-not version-scheme-fn (throw (Exception. (str "No version scheme " scheme " found! version scheme currently supported are: \"maven\" or \"semver\" "))))
     (when-not state (log/warn "No Git data available! is it a git repository? is there a proper .git dir? if so is there any commits? return default starting version"))
     (apply version-scheme-fn state))))

(defn module-name
  "Determine the name for the project by analyzing the environment, path until the git root or folder name if just under the root"
  []
  (if (dedicated-repo?)
    (-> (git/toplevel)
        (fs/split)
        (last))
    ;;monorepo
    (git/prefix)))

(def cli-options
  [["-vs" "--version-scheme SCHEME" "Version Scheme ('maven' or 'semver')"
    :default "semver"
    :validate #{"semver" "maven"}]])

(defn -main
  "Display the current version obtained from the SCM environment"
  [& args]
  (parse-opts args cli-options)
  ;(println (git/toplevel))
  (println (str (module-name) "-" (version)))
  (println (str (version)))
  (shutdown-agents))
