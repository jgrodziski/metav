(ns metav.cli.common
  (:require
    [clojure.spec.alpha :as s]))



(def default-options #:metav.cli{:verbose? false})

(s/def :metav.cli/verbose? boolean?)