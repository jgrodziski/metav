(ns metav.cli.common
  (:require
    [clojure.spec.alpha :as s]
    [clojure.tools.cli :as cli]
    [clojure.string :as string]
    [metav.api.context :as m-ctxt]))

;; TODO: add the option to load all config from an edn file.
;;----------------------------------------------------------------------------------------------------------------------
;; Common conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  (merge m-ctxt/default-metav-opts
         #:metav.cli{:verbose? false}))

(s/def :metav.cli/verbose? boolean?)


;;----------------------------------------------------------------------------------------------------------------------
;; Common cli opts
;;----------------------------------------------------------------------------------------------------------------------
(def cli-options
  [["-h" "--help" "Help"]

   ["-v" "--verbose" "Verbose, output the metadata as json in stdout if the option is present"
    :id :metav.cli/verbose?
    :default (:metav.cli/verbose? default-options)]

   [nil "--full-name" "Use full name format for the artefact-name, prefixing the module name with the name of the top level dir of the project."
    :id :metav/use-full-name?
    :default (:metav/use-full-name? default-options)]

   ["-r" "--module-name-override MODULE-NAME" "Module Name Override"
    :id :metav/module-name-override
    :default (:metav/module-name-override default-options)
    :validate [(partial s/valid? :metav/module-name-override)]]

   ["-s" "--version-scheme SCHEME" "Version Scheme ('maven' or 'semver')"
    :id :metav/version-scheme
    :default (:metav/version-scheme default-options)
    :parse-fn keyword
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
  [option-spec usage-fn args->opts]
  (fn
    [args]
    (let [{:keys [options errors summary] :as opts} (cli/parse-opts args option-spec)]
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
        (if-let [ret (args->opts opts)]
          {:custom-opts ret
           :exit? false
           :ok? true}

          {:exit? true
           :exit-message (usage-fn summary)
           :ok? false}))))); failed custom validation => exit with usage summary


(defn make-main
  "Function helping in defining the main function of a program.

  Parameters:
  - `validate-args-fn`: takes program arguments, parses and validates them.
  - `args->context-fn`: turn the parsed arguments into a metav context
  - `perform-command-fn`: function perfoming a metav command, takes a context as parameter."
  [validate-args-fn
   args->context-fn
   perform-command-fn]
  (fn [& args]
    (let [{:keys [exit? custom-opts] :as parsed-and-validated} (validate-args-fn args)]
      (if exit?
        parsed-and-validated
        (let [res (-> custom-opts
                      args->context-fn
                      perform-command-fn)]
          (assoc parsed-and-validated
            :ret res
            :exit? true))))))


;;----------------------------------------------------------------------------------------------------------------------
;; Helpers
;;----------------------------------------------------------------------------------------------------------------------
(defn basic-custom-args-validation
  "Function intended to be used as a default for the `args->opts` parameter of
  the `make-validate-args` function."
  [parsed-args]
  (let [options (:options parsed-args)]
    (when (> (count options) 1)
      {:options options})))


(defn basic-args->context [validated-args]
  "Function intended to be used as a default for the `args->context-fn` parameter of
  the `make-main` function."
  (m-ctxt/make-context (:options validated-args)))

