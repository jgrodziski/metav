(ns metav.cli.display
  (:require
    [clojure.spec.alpha :as s]
    [clojure.tools.logging :as log]
    [clojure.string :as string]
    [metav.cli.common :as cli-common]
    [metav.api :as api]))


(def cli-options
  (conj cli-common/cli-options
        ["-f" "--output-format FORMAT" "Output format (edn, json, tab)"
         :id :metav.display/output-format
         :parse-fn keyword
         :validate [(partial s/valid? :metav.display/output-format)
                    "Format must be among: edn, json, tab"]]))


(defn usage [summary]
  (->> ["Metav's \"display\" function print the module's name and the current version separated by a tab (for easy \"cut\" in shell script), default, or full metadata as json or edn:"
        ""
        "Usage: metav.display [options]"
        ""
        "Options:"
        summary
        ""]
       (string/join \newline)))


(def validate-args
  (cli-common/make-validate-args cli-options usage))


(defn perform! [context]
  (let [{:metav/keys [module-name-override artefact-name]} context
        output-format (:metav.display/output-format context)]
    (log/debug "Display artifact name and version in format " output-format
               ". Module-name override? " module-name-override
               ", hence artefact-name is" artefact-name)
    (api/display context)))


(def main* (cli-common/make-main
             validate-args
             perform!))


(comment
  (validate-args ["-c" "resources-test/example-conf.edn"])
  (main* "-c" "resources-test/example-conf.edn"))

(def main (cli-common/wrap-exit main*))

