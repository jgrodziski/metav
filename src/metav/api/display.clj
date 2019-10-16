(ns metav.api.display
  (:require
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [metav.api.common :as m-a-c]))

;;----------------------------------------------------------------------------------------------------------------------
;; Display conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  #:metav.display{:output-format :tab})

(s/def :metav.display/output-format #{:edn :json :tab})

(s/def :metav.display/options
  (s/keys :opt [:metav.display/output-format]))

;;----------------------------------------------------------------------------------------------------------------------
;; Perform-display
;;----------------------------------------------------------------------------------------------------------------------

(defmulti perform*! :metav.display/output-format)


(defmethod perform*! :edn [context]
  (print (m-a-c/metadata-as-edn context)))


(defmethod perform*! :json [context]
  (print (json/write-str (m-a-c/metadata-as-edn context))))


(defmethod perform*! :tab [{:metav/keys [artefact-name version]}];default is tab separated module-name and version
  (print (str artefact-name "\t" (str version))))


(defn perform! [context]
  (s/assert :metav.display/options context)
  (perform*! context))