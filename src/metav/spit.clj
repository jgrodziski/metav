(ns metav.spit
  (:require [metav.display :refer [version module-name tag]]
            [metav.git :as git]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]))

(defn metadata
  "return a map of the repo metadata: version, name, path, etc."
  [working-dir tag version]
  (let [prefix (git/prefix working-dir)]
    {:module-name (module-name working-dir)
     :tag tag
     :version (str version)
     :sha (git/last-sha working-dir)
     :path (if prefix prefix ".")}))

(defn metadata-json-str
  "returns a string of the repo metadata as JSON to be included in the message of the annotated tag"
  [working-dir tag version]
  (json/write-str (metadata working-dir tag version)))

(def cli-options
  [["-o" "--output-dir" "Output Directory"
    :default "resources"]
   ["-ns" "--namespace" "Namespace used in code output"
    :default "meta"]
   ["-f" "--formats" "Comma-separated list of output formats (clj, cljc, cljs, edn, json)"
    :default "edn"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["The spit function of Metav output module's metadata in different files: clj, cljc, cljs, edn or json."
        "The metadata is composed of: module-name, tag, version, sha and path"
        ""
        "Usage: metav.spit [options]"
        ""
        "Options:"
        options-summary
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
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      ;; custom validation on arguments
      (> (count options) 1)
      {:options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(def accepted-formats #{"clj" "cljs" "cljc" "edn" "json"})

(defn parse-formats
  "parse a string of comma-separated formats a return a set of formats"
  [s]
  (when s (set (string/split s #","))))

(defn spit-files [output-dir namespace requested-formats]
  (doseq [format requested-formats]
   ;TODO 
    ))

(defn -main
  ""
  [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)
        {:keys [output-dir namespace formats]} options
        requested-formats (parse-formats formats)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (do 
        (prn options)
        (prn requested-formats))
      ;;spit metadata
      
      )))
