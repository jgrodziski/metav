(ns metav.display
  (:require [clojure.tools.logging :as log]
            [metav.maven];;require seems to be necessary when invoking dynamic resolve in the main from the CLI
            [metav.semver]
            [metav.git :as git]))

(defn- version
  "Determine the version for the project by dynamically interrogating the environment"
  [{from-scm :from-scm :or {from-scm 'metav.maven/from-scm}}]
  (log/debug "from-scm" from-scm)
  (let [f (ns-resolve (the-ns 'metav.display) from-scm)
        scm (git/version)]
    (when-not scm (log/warn "No SCM data available! is it a git repository? is there a proper .git dir?"))
    (apply f scm)))

(defn- name
  "Determine the name for the project by analyzing the environment, path until the git root or folder name if just under the root"
  []
  (git/prefix))

(defn -main
  "Display the current version obtained from the SCM environment"
  [& args]
  (println (name))
  (println (str (version {:from-scm 'metav.semver/from-scm})))
  (shutdown-agents))
