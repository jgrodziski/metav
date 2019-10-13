(ns metav.cli.display
  (:require
    [clojure.tools.logging :as log]
    [clojure.tools.cli :as cli]
    [clojure.string :as string]
    [metav.cli.common :as m-cli-common]
    [metav.api.display :as m-display]))


(def default-options
  (merge m-cli-common/default-options
         m-display/default-display-opts))


(def cli-options
  (conj m-cli-common/cli-options
        ["-f" "--output-format FORMAT" "Output format (edn, json, tab)"
         :id :metav.display/output-format
         :default (:metav.display/output-format default-options)
         :parse-fn keyword
         :validate [(partial m-cli-common/validate-option :metav.display/output-format)
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
  (m-cli-common/make-validate-args cli-options
                                   usage
                                   m-cli-common/basic-custom-args-validation))


(defn perform! [context]
  (let [{:metav/keys [module-name-override artefact-name]} context
        output-format (:metav.display/output-format context)]
    (log/debug "Display artifact name and version in format " output-format
               ". Module-name override? " module-name-override
               ", hence artefact-name is" artefact-name)
    (m-display/perform! context)))


(def main* (m-cli-common/make-main
             validate-args
             m-cli-common/basic-args->context
             perform!))


(def main (m-cli-common/wrap-exit main*))

