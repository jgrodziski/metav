(ns metav.cli.release
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [clojure.data.json :as json]
    [metav.cli.common :as cli-common]
    [metav.cli.spit :as cli-spit]
    [metav.api :as api]))


(def cli-options
  (conj cli-spit/cli-options
        [nil "--without-sign" "Should the git tag used for release be signed with the current user's GPG key configured with git"
         :id :metav.git/without-sign
         :default-desc "false"]
        [nil "--spit" "Indicates the release process should spit the metadata file as with the \"spit\" task, in that case the spit options must be provided"
         :id :metav.release/spit
         :default-desc "false"]
        ["-w" "--without-push" "Execute the release process but without pushing at the end, if you want to control the pushing instant yourself"
         :id :metav.release/without-push
         :default-desc "false"]))

;;----------------------------------------------------------------------------------------------------------------------
;; Assembling Release main
;;----------------------------------------------------------------------------------------------------------------------
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


(defn handle-cli-arguments [processed-cli-opts]
  (let [{:keys [arguments]} processed-cli-opts]
    (if-let [potential-level (first arguments)]
      (let [level (cli-common/parse-potential-keyword potential-level)]
        (if (s/valid? :metav.release/level level)
          (update processed-cli-opts :custom-opts assoc :metav.release/level level)
          (assoc processed-cli-opts
            :exit-message (s/explain-str :metav.release/level level))))
      (assoc processed-cli-opts
        :exit-message "Release level not properly specified."))))

(def validate-cli-args (cli-common/make-validate-args cli-options usage handle-cli-arguments))


(defn perform! [context]
  (let [{:metav.cli/keys [verbose?]
         :metav.release/keys [spit level]} context]
    (log/debug "Release level is " level ". Assert everything is committed, bump the version, tag and push.")
    (log/debug (str "Spitting metadata requested? " spit ". "
                    (if spit
                      (let [{:metav.spit/keys [output-dir namespace formats]} context]
                        "Spitting metadata (module-name, tag, version, sha, timestamp) in dir " output-dir
                        " with namespace " namespace
                        " and formats " formats)
                      "")))
    (let [{bumped-tag :metav/tag :as bumped-context} (api/release! context)]
      (if verbose?
        (println (json/write-str (api/metadata-as-edn context)))
        (println bumped-tag))
      bumped-context)))


(def main* (cli-common/make-main validate-cli-args perform!))


(def main (cli-common/wrap-exit main*))
