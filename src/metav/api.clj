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


(defn options-valid? [opts]
  (s/valid? :metav/options opts))


(defn make-context
  ([]
   (make-context {:metav/working-dir (u/pwd)}))
  ([opts]
   (m-ctxt/make-context
     (s/assert* :metav/options
                (merge default-options opts)))))


(defn display [context]
  (m-display/perform! context))

(defn spit! [context]
  (m-spit/perform! context))

(defn release! [context]
  (m-release/perform! context))