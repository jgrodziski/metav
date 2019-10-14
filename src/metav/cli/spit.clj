(ns metav.cli.spit
  (:require
    [clojure.string :as string]
    [metav.cli.common :as m-cli-common]
    [metav.api.spit :as m-spit]))


;;----------------------------------------------------------------------------------------------------------------------
;; Spit conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  (merge m-cli-common/default-options
         m-spit/defaults-spit-opts))


(defn parse-formats [s]
  (into #{}
        (comp
          (map string/trim)
          (map keyword))
        (string/split s #",")))


(def cli-options
  (conj m-cli-common/cli-options

        ["-o" "--output-dir DIR_PATH" "Output Directory"
         :id :metav.spit/output-dir
         :default (:metav.spit/output-dir default-options)
         :default-desc (:metav.spit/output-dir default-options)
         :parse-fn str]

        ["-n" "--namespace NS" "Namespace used in code output"
         :id :metav.spit/namespace
         :default (:metav.spit/namespace default-options)]

        ["-f" "--formats FORMATS" "Comma-separated list of output formats (clj, cljc, cljs, edn, json)"
         :id :metav.spit/formats
         :default (:metav.spit/formats default-options)
         :parse-fn parse-formats
         :validate [(partial m-cli-common/validate-option :metav.spit/formats)
                    "Formats must be in the following list: clj, cljc, cljs, edn, json"]]

        ["-t" "--template TEMPLATE" "Template used for rendering (must follows mustache format, spitted data is available during template rendering)"
         :id :metav.spit/template
         :parse-fn str
         :validate [(partial m-cli-common/validate-option :metav.spit/template)
                    "Template must be a valid path to a java resource."]]

        ["-d" "--rendering-output RENDERING-OUTPUT" "File to render template in"
         :id :metav.spit/rendering-output
         :parse-fn str
         :validate [(partial m-cli-common/validate-option :metav.spit/rendering-output)
                    "Rendering output must be a path to an existing directory in the project."]]))

;;----------------------------------------------------------------------------------------------------------------------
;; Assembling spit main
;;----------------------------------------------------------------------------------------------------------------------
(defn usage [summary]
  (->> ["The spit function of Metav output module's metadata in files according the given formats among: clj, cljc, cljs, edn or json."
        "The metadata is composed of: module-name, tag, version, path, timestamp "
        ""
        "Usage: metav.spit [options]"
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
  (m-spit/perform! context))


(def main* (m-cli-common/make-main
             validate-args
             m-cli-common/basic-args->context
             perform!))

(comment
  (main* "-f" "cljc, json,edn"
         "-n" "metav.meta"
         "-t" "mustache-template.txt"
         "-d" "resources-test/rendered.txt"))

(def main (m-cli-common/wrap-exit main*))