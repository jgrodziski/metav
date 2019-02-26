(ns metav.display-cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]))

(defn usage [summary]
  (->> ["Metav's \"display\" function print the module's name and the current version separated by a tab (for easy \"cut\" in shell script), default, or full metadata as json or edn:"
        ""
        "Usage: metav.display [options]"
        ""
        "Options:"
        summary
        ""]
       (string/join \newline)))

(def accepted-formats #{"edn" "json" "tab"})

(defn parse-format
  "parse a format string formats a return a set of formats"
  [s]
  (when s (set (string/split s #","))))

(def default-options {:version-scheme "semver" :format "tab"})

(def cli-options
  [["-vs" "--version-scheme SCHEME" "Version Scheme ('maven' or 'semver')"
    :default "semver"
    :validate #{"semver" "maven"}]
   ["-f" "--output-format FORMAT" "Output format (edn, json, tab)"
    :default (:formats default-options)
    :validate [#(accepted-formats %) "Format must be among: edn, json, tab"]]
   ["-r" "--module-name-override MODULE-NAME" "Module Name Override"
    :default nil]])

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

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
      (> (count options) 1)
      {:options options}

      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary) :ok? false})))

(defn exit [status msg]
  (println msg)
  (System/exit status))
