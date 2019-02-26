(ns metav.display
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [metav.maven];;require seems to be necessary when invoking dynamic resolve in the main function from the CLI
            [metav.semver]
            [metav.metadata :refer [invocation-context metadata-as-edn]]
            [metav.display-cli :refer [validate-args exit]]))

(defmulti spit-stdout! :output-format)

(defmethod spit-stdout! "edn" [invocation-context]
  (print (metadata-as-edn invocation-context)))

(defmethod spit-stdout! "json" [invocation-context]
  (print (json/write-str (metadata-as-edn invocation-context))))

(defmethod spit-stdout! :default [{:keys [module-name version] :as invocation-context}];default is tab separated module-name and version
  (print (str module-name "\t" (str version))))

(defn -main
  "Display the artifact name built from the module name + the current version obtained from the SCM environment"
  [& args]
  (let [{:keys [level options exit-message ok?]} (validate-args args)
        _ (when exit-message (exit (if ok? 0 1) exit-message))
        {:keys [output-format module-name-override module-name] :as invocation-context} (invocation-context options)]
    (log/debug "Display artifact name and version in format " output-format ". Module-name override? " module-name-override ", hence module-name is" module-name)
    (spit-stdout! invocation-context)
    (shutdown-agents)))
