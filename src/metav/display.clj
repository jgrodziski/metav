(ns metav.display
  (:require
    [metav.cli.display :as m-display-cli]))


(def -main
  "Display the artifact name built from the module name + the current version obtained
  from the SCM environment."
  m-display-cli/main)