(ns metav.domain.git-operations
  (:require
    [clojure.spec.alpha :as s]
    [clojure.data.json :as json]
    [metav.domain.context]
    [metav.domain.common :as common]
    [metav.domain.git :as git]
    [metav.utils :as utils]))


(s/def ::working-dir-present (s/keys :req [:metav/working-dir]))
(s/def ::top-level-present (s/keys :req [:metav/top-level]))

;;----------------------------------------------------------------------------------------------------------------------
;;----------------------------------------------------------------------------------------------------------------------
(defn check-committed? [context]
  (-> context
      (->> (utils/check-spec ::working-dir-present))
      (-> :metav/working-dir
          git/assert-committed?))
  context)


;;----------------------------------------------------------------------------------------------------------------------
;;----------------------------------------------------------------------------------------------------------------------
(def default-tag-options
  #:metav.git{:without-sign false})


(s/def :metav.git/without-sign boolean?)

(s/def :metav.git.tag-repo/options (s/keys :opt [:metav.git/without-sign]))

(s/def ::tag-repo!-param (s/and (s/merge :metav/context
                                         :metav.git.tag-repo/options)
                                check-committed?))

(defn tag-repo! [context]
  (let [context (utils/merge&validate context default-tag-options ::tag-repo!-param)
        {:metav/keys  [top-level tag]
         :metav.git/keys [without-sign]} context
        annotation (json/write-str (common/metadata-as-edn context))
        tag-result (apply git/tag! top-level
                          tag
                          annotation
                          (when without-sign [:sign false]))]
    (if (int? (first tag-result)) ;;error exit code if so return stderr
      (throw (Exception. (str "Error with git tag command:" (get tag-result 2))))
      (assoc context
        :metav.release/tag-result {:git-res tag-result
                                   :tag tag
                                   :annotation annotation}))))

;;----------------------------------------------------------------------------------------------------------------------
;;----------------------------------------------------------------------------------------------------------------------
(defn commit! [context msg]
  (let [commit-res (-> context
                       (->> (utils/check-spec ::working-dir-present))
                       (-> :metav/working-dir
                           (git/commit! msg)))]
    (assoc context :metav.git/committed
                   {:commit-res commit-res
                    :msg msg})))

;;----------------------------------------------------------------------------------------------------------------------
;;----------------------------------------------------------------------------------------------------------------------
(defn push! [context]
  (utils/check-spec ::top-level-present context)
  (let [top-level (:metav/top-level context)]
    (assoc context :metav.release/push-result (git/push! top-level))))