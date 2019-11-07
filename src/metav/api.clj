(ns metav.api
  (:require
    [clojure.spec.alpha          :as s]
    [clojure.data.json           :as json]
    [metav.utils                 :as utils]
    [metav.domain.common         :as common]
    [metav.domain.context        :as context]
    [metav.domain.display        :as display]
    [metav.domain.git-operations :as git-ops]
    [metav.domain.release        :as release]
    [metav.domain.spit           :as spit]))



(defn make-context
  ([]
   (make-context {}))
  ([opts]
   (let [working-dir (:metav/working-dir opts)
         opts (cond-> opts
                      (not working-dir) (assoc :metav/working-dir (utils/pwd)))]
     (context/make-context opts))))


(defn check-context [context]
  (utils/check-spec :metav/context context))


(def metadata-as-edn common/metadata-as-edn)


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
   (spit/spit! context)))


(defn git-add-spitted! [context]
  (spit/git-add-spitted! context))


;;----------------------------------------------------------------------------------------------------------------------
;; git!
;;----------------------------------------------------------------------------------------------------------------------

(defn check-commited [context]
  (git-ops/check-committed? context))


(defn tag-repo! [context]
  (git-ops/tag-repo! context))


(defn commit! [context msg]
  (git-ops/commit! context msg))


(defn push! [context]
  (git-ops/push! context))

;;----------------------------------------------------------------------------------------------------------------------
;; Release!
;;----------------------------------------------------------------------------------------------------------------------

(defn bump [context]
  (release/bump-context context))


(defn release!
  ([] (release! (make-context)))
  ([context]
   (release/release! context)))

