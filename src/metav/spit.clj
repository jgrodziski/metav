(ns metav.spit
  (:require
    [metav.cli.spit :as m-spit-cli]))

(def -main
  "Main function for the spit functionality when used from the command line."
  m-spit-cli/main)