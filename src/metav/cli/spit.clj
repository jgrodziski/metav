(ns metav.cli.spit
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [clojure.data.json :as json]
    [metav.cli.common :as cli-common]
    [metav.api :as api]))


;;----------------------------------------------------------------------------------------------------------------------
;; Spit conf
;;----------------------------------------------------------------------------------------------------------------------
(defn parse-formats [s]
  (into #{}
        (comp
          (map string/trim)
          (map keyword))
        (string/split s #",")))


(def cli-options
  (conj cli-common/cli-options

        ["-o" "--output-dir DIR_PATH" "Output Directory"
         :id :metav.spit/output-dir
         :parse-fn str]

        ["-n" "--namespace NS" "Namespace used in code output"
         :id :metav.spit/namespace]

        ["-p" "--pom" "Indicates the spit/release process should update (or create) the pom.xml file for the project"
         :id :metav.spit/pom
         :default-desc "false"]

        ["-f" "--formats FORMATS" "Comma-separated list of output formats (clj, cljc, cljs, edn, json)"
         :id :metav.spit/formats
         :parse-fn parse-formats
         :validate [(partial s/valid? :metav.spit/formats)
                    "Formats must be in the following list: clj, cljc, cljs, edn, json"]]

        ["-t" "--template TEMPLATE" "Template used for rendering (must follows mustache format, spitted data is available during template rendering)"
         :id :metav.spit/template
         :parse-fn str
         :validate [(partial s/valid? :metav.spit/template)
                    "Template must be a valid path to a java resource."]]

        ["-d" "--rendering-output RENDERING-OUTPUT" "File to render template in"
         :id :metav.spit/rendering-output
         :parse-fn str
         :validate [(partial s/valid? :metav.spit/rendering-output)
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


(def validate-args-fn (cli-common/make-validate-args cli-options usage))

(defn spit! [context]
  (when (:metav.cli/verbose? context)
    (-> context api/metadata-as-edn json/write-str print))
  (api/spit! context))

(def main* (cli-common/make-main validate-args-fn spit!))

(comment
  (main* "-c" "resources-test/example-conf.edn"
         "-f" "cljc, json, edn"
         "-n" "metav.meta"
         "-t" "mustache-template.txt"))

(def main (cli-common/wrap-exit main*))


(comment 
  (defn -main [& args]
    (let [{:keys [exit? ctxt-opts] :as parsed-and-validated} (validate-args-fn args)]
      (if exit?
        parsed-and-validated
        (let [res (try
                    (-> ctxt-opts api/make-context perform-command-fn)
                    (catch ExceptionInfo e
                      {::error e
                       ::error-msg (ex-message e)})
                    (catch Exception e
                      {::error e
                       ::error-msg (.getMessage e)}))]
          (if-let [error (::error-msg res)]
            (assoc parsed-and-validated
                   :ret res
                   :exit? true
                   :ok? false
                   :exit-message error)
            (assoc parsed-and-validated
                   :ret res
                   :exit? true
                   :ok? true)))))))
