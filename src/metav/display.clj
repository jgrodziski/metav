(ns metav.display
  (:require [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [metav.maven];;require seems to be necessary when invoking dynamic resolve in the main function from the CLI
            [metav.semver]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.git :as git :refer [pwd]]
            [me.raynes.fs :as fs]))

(def ^:dynamic *scheme* "semver")
(def ^:dynamic *separator* "-")


(defn module-name
  "Determine the name for the project by analyzing the environment, path until the git root or folder name if just under the root"
  ([] (module-name (pwd)))
  ([working-dir]
   (if (dedicated-repo? working-dir)
     (-> (git/toplevel working-dir)
         (fs/split)
         (last))
     ;;monorepo
     (let [name (clojure.string/replace (git/prefix working-dir) "/" "-")]
       (subs name 0 (dec (count name)))))))

(defn prefix
  "return the prefix used before the version in tag (not to be confused with the git prefix even if we deduce the tag prefix with the git prefix)"
  ([] (prefix (pwd)))
  ([working-dir & {:keys [separator] :or {separator *separator*}}]
   (if (monorepo? working-dir)
     (str (module-name working-dir) separator)
     "v";in case of dedicated repo the prefix is just a "v"
     )))

(defn tag
  "in case of monorepo return the tag as the module name concatenated with the version"
  ([version] (tag (pwd) version))
  ([working-dir version & {:keys [separator] :or {separator *separator*}}]
   (str (prefix working-dir :separator separator) version)))

(defn- version-scheme-fn [scheme]
  (ns-resolve (the-ns 'metav.display) (symbol (str "metav." scheme "/version"))))

(defn version
  "Determine the version for the project by dynamically interrogating the environment,
  you can choose the \"maven\" or \"semver\" version scheme"
  ([] (version nil :scheme "semver"));default value is semver
  ([working-dir & {:keys [scheme separator]
                   :or {scheme *scheme*
                        separator *separator*}}]
   (let [version-scheme-fn (version-scheme-fn (clojure.string/lower-case scheme))
         state (git/working-copy-description working-dir :prefix (prefix working-dir :separator separator) :min-sha-length 4)]
     (when-not version-scheme-fn
       (throw (Exception. (str "No version scheme " scheme " found! version scheme currently supported are: \"maven\" or \"semver\" "))))
     (when-not state
       (log/warn "No Git data available in directory "working-dir"! is it a git repository?
                  is there a proper .git dir? if so is there any commits? return default starting version"))
     (apply version-scheme-fn state))))

(defn artefact-name
  ([] (artefact-name (pwd)))
  ([working-dir & {:keys [scheme separator] :or {scheme *scheme* separator *separator*}}]
   (str (prefix working-dir :separator separator) (version working-dir :scheme scheme :separator separator))))

(def cli-options
  [["-vs" "--version-scheme SCHEME" "Version Scheme ('maven' or 'semver')"
    :default "semver"
    :validate #{"semver" "maven"}]])

(defn -main
  "Display the artefact name built from the module name + the current version obtained from the SCM environment"
  [& args]
  (let [working-dir (str (pwd))]
    ;(parse-opts args cli-options)
    (println (artefact-name working-dir :separator "\t"))
    (shutdown-agents)))
