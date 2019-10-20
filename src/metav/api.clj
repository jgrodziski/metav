(ns metav.api
  (:require
    [clojure.spec.alpha :as s]
    [metav.api.context :as m-ctxt]
    [metav.api.display :as m-display]
    [metav.api.spit :as m-spit]
    [metav.api.release :as m-release]
    [metav.utils :as u]
    [metav.api.common :as m-a-c]))


(def default-options
  (merge m-ctxt/default-options
         m-display/default-options
         m-spit/defaults-options
         m-release/default-options))


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

(def display! m-display/perform!)

(def spit! m-spit/perform!)

(def release! m-release/perform!)