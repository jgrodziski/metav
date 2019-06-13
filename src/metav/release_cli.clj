(ns metav.release-cli
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [metav.spit-cli :as spit-cli]))

(def accepted-levels #{:major :minor :patch})

(def cli-options
  (into [[nil "--without-sign" "Whether the released git tag should be signed with the current user's GPG key."
    :default false
    :default-desc "false"]
         ["-s" "--spit" "Indicates the release process should spit the metadata file as with the \"spit\" task, in that case the spit options must be provided"
          :default false
          :default-desc "false"]
         ["-w" "--without-push" "Execute the release process but without pushing at the end, if you want to control the pushing instant yourself"
          :default false
          :default-desc "false"]]
        spit-cli/cli-options))

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
