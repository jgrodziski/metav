(ns metav.domain.display
  (:require
   [clojure.spec.alpha :as s]))

;;----------------------------------------------------------------------------------------------------------------------
;; Display conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  #:metav.display{:output-format :tab})


(s/def :metav.display/output-format #{:edn :json :tab})


(s/def :metav.display/options
  (s/keys :opt [:metav.display/output-format]))
