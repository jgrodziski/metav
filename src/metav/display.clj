(ns metav.display
  (:require [clojure.tools.logging :as log]
            [metav.git :as git]))

(defn- version
  "Determine the version for the project by dynamically interrogating the environment"
  [{from-scm :from-scm :or {from-scm 'deps-v.maven/from-scm}}]
  (let [f (ns-resolve *ns* from-scm)
        scm (git/version)]
    (when-not scm (log/warn "No SCM data available! is it a git repository? is there a proper .git dir?"))
    (apply f scm)))

(defn -main
  "Display the current version obtained from the SCM environment"
  [& args]
  (println (version))
  (shutdown-agents))
