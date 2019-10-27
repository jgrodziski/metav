(ns metav.display
  (:require
    [metav.cli.display :as cli-display]))


(def -main
  "Display the artifact name built from the module name + the current version obtained
  from the SCM environment."
  cli-display/main)
