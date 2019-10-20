(ns metav.cli.common
  (:require
    [clojure.spec.alpha :as s]
    [clojure.tools.cli :as cli]
    [clojure.string :as string]
    [clojure.edn :as edn]
    [me.raynes.fs :as fs]
    [metav.api :as m-api]))


;;----------------------------------------------------------------------------------------------------------------------
;; Helper
;;----------------------------------------------------------------------------------------------------------------------
(defn parse-potential-keyword
  "return the correct level in the accepted ones (major, minor, patch) or nil otherwise"
  [arg]
  (if (clojure.string/starts-with? arg ":")
    (keyword (subs arg 1 (count arg)))
    (keyword arg)))

;;----------------------------------------------------------------------------------------------------------------------
;; Common conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  (merge m-api/default-options
         #:metav.cli{:verbose? false}))

(s/def :metav.cli/verbose? boolean?)
(s/def :metav.cli/config fs/exists?)

;;----------------------------------------------------------------------------------------------------------------------
;; Common cli opts
;;----------------------------------------------------------------------------------------------------------------------
(def cli-options
  [["-h" "--help" "Help"]

   ["-v" "--verbose" "Verbose, output the metadata as json in stdout if the option is present"
    :id :metav.cli/verbose?]

   ["-c" "--config-file PATH" "Edn file containing a map of metav config."
    :id :metav.cli/config
    :validate [(partial s/valid? :metav.cli/config) "Config file invalid."]]

   [nil "--full-name" "Use full name format for the artefact-name, prefixing the module name with the name of the top level dir of the project."
    :id :metav/use-full-name?]

   ["-r" "--module-name-override MODULE-NAME" "Module Name Override"
    :id :metav/module-name-override
    :validate [(partial s/valid? :metav/module-name-override) "Modudle name override must be a non empty string."]]

   ["-s" "--version-scheme SCHEME" "Version Scheme ('maven' or 'semver')"
    :id :metav/version-scheme
    :parse-fn parse-potential-keyword
    :validate [(partial s/valid? :metav/version-scheme)
               "The -s or --version-scheme option only accepts the values: 'maven' or 'semver'"]]])

;;----------------------------------------------------------------------------------------------------------------------
;; Common handling of cli args
;;----------------------------------------------------------------------------------------------------------------------

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))


(defn exit
  "Function used to cleanly shutdown the program. Expect a map as an argument.
  Relevant keys are:
  - `:ok?`: a boolean from wich the status code is derived.
  - `:exit-message`: An optional message to display at the end of the program."
  [{:keys [exit-message ok?]}]
  (let [status (if ok? 0 1)]
    (when exit-message (println exit-message))
    (shutdown-agents)
    (System/exit status)))


(defn wrap-exit
  "Wraps a function intended to be the main function of a program.
  The result of the wripping is a main function that will executed the wrapped one then
  forward its result to the exit function."
  [f]
  (fn wrapped-main [& args]
    (let [res (apply f args)]
      (exit res))))


(defn process-parsed-opts [parsed]
  (let [{:keys [options]} parsed
        file-config (:metav.cli/config options)
        definitive-options (if file-config
                             (merge (-> file-config slurp edn/read-string)
                                    (dissoc options :metav.cli/config))
                             options)]
    (if (s/valid? :metav/options definitive-options)
      (assoc parsed :custom-opts definitive-options)
      (assoc parsed :exit-message (s/explain-str :metav/options definitive-options)))))

(defn make-validate-args
  "Makes a function that validates command line arguments. This function
  either return a map indicating the program should exit
  (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided.

  Parameters:
  - `option-spec`: clojure.tools.cli vector defining opts.
  - `usage-fn: usage function employed to print a desctiption of the command.`
  - `args->opts`: Function taking the result of clojure.tools.cli/parse-opts
  (parsed program arguments) and returning either a map containing the customarily verified
  arguments to be used by the program."
  ([option-spec usage-fn]
   (make-validate-args option-spec usage-fn identity))
  ([option-spec usage-fn cli-arguments->opts]
   (fn
     [args]
     (let [{:keys [options errors summary] :as cli-parse-result} (cli/parse-opts args option-spec)]
       (cond
         (:help options) ; help => exit OK with usage summary
         {:exit? true
          :exit-message (usage-fn summary)
          :ok? true}

         errors ; errors => exit with description of errors
         {:exit? true
          :exit-message (error-msg errors)
          :ok? false}

         ;; custom validation on arguments
         :else
         (let [processed (process-parsed-opts cli-parse-result)]
           (if-let [exit-msg (:exit-message processed)]
             {:exit? true
              :ok? false
              :exit-message exit-msg}
             (let [custom-processed (cli-arguments->opts processed)]
               (if-let [exit-msg (:exit-message custom-processed)]
                 {:exit? true
                  :ok? false
                  :exit-message exit-msg} ; failed custom validation))
                 {:exit?     false
                  :ok?       true
                  :ctxt-opts (:custom-opts custom-processed)})))))))))


(defn make-main
  "Function helping in defining the main function of a program.

  Parameters:
  - `validate-args-fn`: takes program arguments, parses and validates them.
  - `args->context-fn`: turn the parsed arguments into a metav context
  - `perform-command-fn`: function perfoming a metav command, takes a context as parameter."
  [validate-args-fn perform-command-fn]
  (fn [& args]
    (let [{:keys [exit? ctxt-opts] :as parsed-and-validated} (validate-args-fn args)]
      (if exit?
        parsed-and-validated
        (let [res (-> ctxt-opts m-api/make-context perform-command-fn)]
          (assoc parsed-and-validated
            :ret res
            :exit? true
            :ok? true))))))
