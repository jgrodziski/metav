(ns metav.spit
  (:require [metav.metadata :refer [invocation-context metadata-as-code metadata-as-edn]]
            [metav.git :as git]
            [metav.spit-cli :refer [parse-formats validate-args exit]]
            [clojure.java.io :refer [file]]
            [clojure.data.json :as json]
            [cljstache.core :refer [render-resource]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]

            [metav.cli.spit :as m-spit-cli]))
(comment

  (defn render! [{:keys [template rendering-output] :as invocation-context} version]
    (let [metadata (metadata-as-edn invocation-context version)]
      (spit rendering-output (render-resource template metadata))
      (str rendering-output)))

  (defn -main
    [& args]
    (let [{:keys [options exit-message ok?]} (validate-args args)
          {:keys [version] :as invocation-context} (invocation-context options)
          metadata (metadata-as-edn invocation-context version)]
      (when exit-message
        (exit (if ok? 0 1) exit-message))
      (spit-files! invocation-context version);spit files invoked from CLI deduce the current version from git state
      (if (:template options)
        (render! invocation-context version))
      (if (:verbose options)
        (print (json/write-str metadata)))
      (shutdown-agents))))

(def -main m-spit-cli/main)