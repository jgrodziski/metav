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

(defn check-committed? [context]
  (-> context
      (->> (utils/check-spec ::working-dir-present))
      (-> :metav/working-dir
          git/assert-committed?))
  context)


(s/def ::tag-repo!-param (s/and :metav/context
                                check-committed?))


(defn tag-repo! [bumped-context]
  (let [{:metav/keys  [top-level tag]
         :metav.release/keys [without-sign]} (utils/check-spec ::tag-repo!-param bumped-context)
        annotation (json/write-str (common/metadata-as-edn bumped-context))
        tag-result (apply git/tag! top-level
                          tag
                          annotation
                          (when without-sign [:sign false]))]
    (if (int? (first tag-result)) ;;error exit code if so return stderr
      (throw (Exception. (str "Error with git tag command:" (get tag-result 2))))
      (assoc bumped-context
        :metav.release/tag-result {:git-res tag-result
                                   :tag tag
                                   :annotation annotation}))))



(defn commit! [context msg]
  (let [commit-res (-> context
                       (->> (utils/check-spec ::working-dir-present))
                       (-> :metav/working-dir
                           (git/commit! msg)))]
    (assoc context :metav.git/committed
                   {:commit-res commit-res
                    :msg msg})))


(defn push! [context]
  (utils/check-spec ::top-level-present context)
  (let [top-level (:metav/top-level context)]
    (assoc context :metav.release/push-result (git/push! top-level))))