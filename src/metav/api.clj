(ns metav.api
  (:require
    [clojure.spec.alpha :as s]
    [clojure.data.json    :as json]
    [metav.domain.context :as m-ctxt]
    [metav.domain.display :as display]
    [metav.domain.spit    :as spit]
    [metav.domain.release :as release]
    [metav.utils :as u]
    [metav.domain.common :as m-a-c]))

(def default-options
  (merge m-ctxt/default-options
         display/default-options
         spit/defaults-options
         release/default-options))

(s/def :metav/options (s/and :metav.context/options
                             :metav.display/options
                             :metav.spit/options
                             :metav.release/options))


(defn make-context
  ([]
   (make-context {}))
  ([opts]
   (let [working-dir (:metav/working-dir opts)
         opts (cond-> opts
                      (not working-dir) (assoc :metav/working-dir (u/pwd)))]
     (m-ctxt/make-context
       (s/assert* :metav/options
                  (merge default-options opts))))))


(def metadata-as-edn m-a-c/metadata-as-edn)

;;----------------------------------------------------------------------------------------------------------------------
;; Display
;;----------------------------------------------------------------------------------------------------------------------

(defmulti display* :metav.display/output-format)


(defmethod display* :edn [context]
  (print (m-a-c/metadata-as-edn context)))


(defmethod display* :json [context]
  (print (json/write-str (m-a-c/metadata-as-edn context))))


(defmethod display* :tab [{:metav/keys [artefact-name version]}];default is tab separated module-name and version
  (print (str artefact-name "\t" (str version))))


(defn display!
  ([] (display! (make-context)))
  ([context]
   (s/assert :metav.display/options context)
   (display* context)
   context))

;;----------------------------------------------------------------------------------------------------------------------
;; Spit!
;;----------------------------------------------------------------------------------------------------------------------

(defn spit!
  ([] (spit! (make-context)))
  ([context] (spit/spit! context)))

;;----------------------------------------------------------------------------------------------------------------------
;; Release!
;;----------------------------------------------------------------------------------------------------------------------

(def release! release/release!)
