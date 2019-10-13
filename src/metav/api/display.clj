(ns metav.api.display
  (:require
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [metav.api.context :as m-ctxt]))

;;----------------------------------------------------------------------------------------------------------------------
;; Display conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-display-opts
  #:metav.display{:output-format :tab})

(s/def :metav.display/output-format #{:edn :json :tab})


;;----------------------------------------------------------------------------------------------------------------------
;; Perform-display
;;----------------------------------------------------------------------------------------------------------------------

(defmulti perform! :metav.display/output-format)


(defmethod perform! :edn [context]
  (print (m-ctxt/metadata-as-edn context)))


(defmethod perform! :json [context]
  (print (json/write-str (m-ctxt/metadata-as-edn context))))


(defmethod perform! :tab [{:metav/keys [artefact-name version]}];default is tab separated module-name and version
  (print (str artefact-name "\t" (str version))))