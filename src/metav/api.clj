(ns metav.api
  (:require
    [clojure.spec.alpha :as s]
    [clojure.data.json    :as json]
    [metav.utils :as utils]
    [metav.domain.common  :as common]
    [metav.domain.context :as context]
    [metav.domain.display :as display]
    [metav.domain.spit    :as spit]
    [metav.domain.release :as release]))


(defn make-context
  ([]
   (make-context {}))
  ([opts]
   (let [working-dir (:metav/working-dir opts)
         opts (cond-> opts
                      (not working-dir) (assoc :metav/working-dir (utils/pwd)))]
     (context/make-context opts))))


(defn assert-context [context]
  (utils/check-spec :metav/context context))

(def metadata-as-edn common/metadata-as-edn)
(def bump-context common/bump-context)

;;----------------------------------------------------------------------------------------------------------------------
;; Display
;;----------------------------------------------------------------------------------------------------------------------

(defmulti display* :metav.display/output-format)


(defmethod display* :edn [context]
  (println (common/metadata-as-edn context)))


(defmethod display* :json [context]
  (println (json/write-str (common/metadata-as-edn context))))


(defmethod display* :tab [{:metav/keys [artefact-name version]}];default is tab separated module-name and version
  (println (str artefact-name "\t" (str version))))


(defn display
  ([] (display (make-context)))
  ([context]
   (-> context
       (utils/merge&validate display/default-options
                             (s/merge :metav/context
                                      :metav.display/options))
       (display*))
   context))

;;----------------------------------------------------------------------------------------------------------------------
;; Spit!
;;----------------------------------------------------------------------------------------------------------------------

(defn spit!
  ([] (spit! (make-context)))
  ([context]
   (->> context
        assert-context
        spit/spit!)))


;;----------------------------------------------------------------------------------------------------------------------
;; Release!
;;----------------------------------------------------------------------------------------------------------------------

(defn release!
  ([] (release! (make-context)))
  ([context]
   (->> context
        assert-context
        release/release!)))
