(ns metav.release
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.tools.logging :as log :refer [debug]]
            [metav
             [git :as git :refer [assert-committed?]]
             [metadata :refer [invocation-context metadata-as-edn tag]]
             [release-cli :refer [accepted-levels exit validate-args]]
             [spit :as spit :refer [spit-files!]]]
            [metav.version.protocols :refer [bump]]))


(comment
  (defn -main [& args]
    (let [{:keys [level options exit-message ok?] :as vargs} (validate-args args)
          {:keys [without-push spit output-dir namespace formats module-name-override] :as invocation-context} (invocation-context options)]
      (when exit-message (exit (if ok? 0 1) exit-message))
      (debug "Release level is " level ". Assert everything is committed, bump the version, tag and push.")
      (debug "Spitting metadata requested? " spit ". If true spit metadata (module-name, tag, version, sha, timestamp) in dir " output-dir " with namespace " namespace " and formats " formats)
      (let [[_ _ tag _] (execute! invocation-context level)]
        (if (:verbose options)
          (print (json/write-str (metadata-as-edn invocation-context (:version invocation-context))))
          (print tag))
        (shutdown-agents)))))
