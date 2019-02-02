(ns metav.release
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [metav.git :as git]
            [metav.display :refer [version tag module-name]]
            [metav.spit :as spit :refer [metadata-as-edn spit-files! ]]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.version.protocols :refer [bump]]
            [clojure.data.json :as json]
            [clojure.set :as set]))

(defn assert-in-module?
  "assert whether the module-dir is really a module (that's to say with a deps.edn file in it)"
  [module-dir]
  ;;TODO implement it :)
  true)

(def default-options (merge spit/default-options {:spit false}))
(def accepted-levels #{:major :minor :patch})

(defn execute!
  "assert that nothing leaves uncommitted or untracked,
  then bump version to a releasable one (depending on the release level),
  commit, tag with the version (hence denoting a release),
  then push
  return [module-name next-version tag push-result]"
  ([scheme level] (execute! nil scheme level))
  ([module-dir scheme level {:keys [spit output-dir namespace formats] :as options}]
   (when-not (accepted-levels level) (throw (Exception. (str "Incorrect level: "level". Accepted levels are:" (string/join accepted-levels ", ")))))
   (log/debug "execute!" module-dir scheme level options)
   (assert-in-module? module-dir)
   (git/assert-committed? module-dir)
   (let [repo-dir (git/toplevel module-dir)
         module-name (module-name module-dir)
         current-version (version module-dir :scheme scheme)
         next-version (bump current-version level)
         tag (tag module-dir next-version)]
     (log/info "Current version of module '" module-name "' is:" (str current-version))
     (log/info "Next version of module '" module-name "' is:" (str next-version))
     (log/info "Next tag is" tag)
     ;;spit meta file and commit
     (when spit
       (let [spitted (spit-files! module-dir next-version options)]
         (git/add! module-dir ".")
         (git/commit! (str "Bump to version" next-version " and spit related metadata in file(s)."))))

     ;then tag
     (git/tag! repo-dir tag (json/write-str (metadata-as-edn module-dir next-version)))
     (let [push-result (git/push! repo-dir)]
       [module-name next-version tag push-result]))))

(def cli-options
  (cons
   ["-s" "--spit" "Indicates the release process should spit the metadata file as with the \"spit\" task, in that case the spit options must be provided"
    :default false]
   spit/cli-options))

(defn usage [summary]
  (->> ["Metav's \"release\" function does the following:"
        "  - assert the command is invoked with a deps.edn in the working directory"
        "  - assert everything is committed (no untracked or uncommitted files)."
        "  - bump the version"
        "  - [optional: spit and commit the version metadata (module-name, tag, version, sha, timestamp) in file(s)]"
        "  - tag the repo with the version prefixed by the module-name in cas of a monorepo"
        "  - push everything"
        ""
        "Usage: metav.release [options] <level>"
        "with <level>: major, minor or patch"
        ""
        "Options:"
        summary
        ""]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn level
  "return the correct level in the accepted ones (major, minor, patch) or nil otherwise"
  [arg]
  (let [level (if (clojure.string/starts-with? arg ":")
                (keyword (subs arg 1 (count arg)))
                (keyword arg))]
    (accepted-levels level)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary] :as opts} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (and (>= (count arguments) 1)
           (not (nil? (level (first arguments)))))
      {:options options :level (level (first arguments))}

      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary) :ok? false})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [level options exit-message ok?] :as vargs} (validate-args args)
        {:keys [spit output-dir namespace formats]} options]
    (log/debug "Release level is " level ". Assert everything is committed, bump the version, tag and push.")
    (log/debug "Spitting metadata requested? " spit ". If true spit metadata (module-name, tag, version, sha, timestamp) in dir " output-dir " with namespace " namespace " and formats " formats)
    (let [[module-name next-version tag push-result] (execute! (str (git/pwd)) "semver" level options)]
      (print tag)
      (shutdown-agents))))
