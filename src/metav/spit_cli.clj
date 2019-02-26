(ns metav.spit-cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.set :as set]))

(def accepted-formats #{"clj" "cljs" "cljc" "edn" "json"})

(defn parse-formats
  "parse a string of comma-separated formats a return a set of formats"
  [s]
  (when s (set (string/split s #","))))

(def default-options {:output-dir "resources" :namespace "meta" :formats "edn" :verbose false})

(def cli-options
  [["-o" "--output-dir DIR_PATH" "Output Directory"
    :default (:output-dir default-options)
    :default-desc "resources"
    :parse-fn str]
   ["-n" "--namespace NS" "Namespace used in code output"
    :default (:namespace default-options)]
   ["-f" "--formats FORMATS" "Comma-separated list of output formats (clj, cljc, cljs, edn, json)"
    :default (:formats default-options)
    :validate [#(empty? (set/difference (parse-formats %) accepted-formats)) "Formats must be in the following list: clj, cljc, cljs, edn, json"]]
   ["-r" "--module-name-override MODULE-NAME" "Module Name Override"
    :parse-fn str]
   ["-v" "--verbose" "Verbose, output the metadata as json in stdout if the option is present"
    :default (:verbose default-options)]
   ["-h" "--help" "Help"]])

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

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with an error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  ;(prn "cli-options" cli-options args)
  (let [{:keys [options arguments errors summary] :as opts} (parse-opts args cli-options)]
    ;(prn opts)
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}

      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}

      ;; custom validation on arguments
      (> (count options) 1)
      {:options options}

      (and (= 0 (count options)) (= 0 (count arguments)))
      {:exit-message (usage summary) :ok? false}

      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary) :ok? false})))

(defn exit [status msg]
  (println msg)
  (System/exit status))
