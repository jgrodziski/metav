(ns metav.cli.release
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [metav.cli.common :as m-c-c]
    [metav.cli.spit :as m-spit-cli]))


(def cli-options
  (conj m-spit-cli/cli-options
        [nil "--without-sign" "Should the git tag used for release be signed with the current user's GPG key configured with git"
         :default false
         :default-desc "false"]
        ["-s" "--spit" "Indicates the release process should spit the metadata file as with the \"spit\" task, in that case the spit options must be provided"
         :default false
         :default-desc "false"]
        ["-w" "--without-push" "Execute the release process but without pushing at the end, if you want to control the pushing instant yourself"
         :default false
         :default-desc "false"]))


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


(defn make-level
  "return the correct level in the accepted ones (major, minor, patch) or nil otherwise"
  [arg]
  (if (clojure.string/starts-with? arg ":")
    (keyword (subs arg 1 (count arg)))
    (keyword arg)))


(defn handle-cli-arguments [processed-cli-opts]
  (let [{:keys [arguments]} processed-cli-opts]
    (if-let [potential-level (first arguments)]
      (let [level (make-level potential-level)]
        (if (s/valid? :metav.release/level level)
          (update processed-cli-opts :custom-opts assoc :metav.release/level level)
          (assoc processed-cli-opts
            :exit-message (s/explain-str :metav.release/level level))))
      (assoc processed-cli-opts
        :exit-message "Release level not properly specified."))))

(def validate-cli-args (m-c-c/make-validate-args cli-options identity handle-cli-arguments))