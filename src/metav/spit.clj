(ns metav.spit
  (:require
    [metav.cli.spit :as cli-spit]))

(def -main
  "Main function for the spit functionality when used from the command line."
  cli-spit/main)
