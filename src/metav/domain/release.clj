(ns metav.domain.release
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [clojure.data.json :as json]
   [clojure.set :refer [union]]
   [metav.utils :as utils]
   [metav.domain.common :as common]
   [metav.domain.git :as git]
   [metav.domain.version.semver :as semver]
   [metav.domain.version.maven :as maven]
   [metav.domain.spit :as spit]))

;;----------------------------------------------------------------------------------------------------------------------
;; Release conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  #:metav.release{:level :patch
                  :without-sign false
                  :spit false
                  :without-push false})

(s/def :metav.release/level (union semver/allowed-bumps maven/allowed-bumps))
(s/def :metav.release/without-sign boolean?)
(s/def :metav.release/spit boolean?)
(s/def :metav.release/without-push boolean?)


(s/def :metav.release/options
  (s/keys :opt [:metav.release/level
                :metav.release/without-sign
                :metav.release/spit
                :metav.release/without-push]))


(defn do-spits-and-commit! [bumped-context]
  (let [{:metav/keys [working-dir artefact-name version]} bumped-context
        ctxt-with-spits (spit/spit! bumped-context)]
    (apply git/add! working-dir (:metav.spit/spitted ctxt-with-spits))
    (git/commit! working-dir
                   (format "Bump module %s to version %s and spit/render related metadata in file(s)." artefact-name version))
    ctxt-with-spits))

(defn tag-repo! [bumped-context]
  (let [{:metav/keys  [top-level tag]
         :metav.release/keys [without-sign]} bumped-context
        tag-result (apply git/tag! top-level
                          tag
                          (json/write-str (common/metadata-as-edn bumped-context))
                          (when without-sign [:sign false]))]
    (if (int? (first tag-result)) ;;error exit code if so return stderr
      (throw (Exception. (str "Error with git tag command:" (get tag-result 2)))))))

(defn bump-level-valid? [context]
  (let [{scheme :metav/version-scheme
         level :metav.release/level} context
        spec (if (= :semver scheme)
               ::semver/accepted-bumps
               ::maven/accepted-bumps)]
    (s/valid? spec level)))


(s/def :metav.release/required (s/keys :req [:metav.release/level]))


(s/def ::release!-params (s/and (s/merge :metav.release/required
                                         :metav.release/options)
                                bump-level-valid?))

(defn release!
  "Assert that nothing leaves uncommitted or untracked,
  then bump version to a releasable one (depending on the release level),
  commit, tag with the version (hence denoting a release), then push.

  Returns the context passed as parameter with the keys `:metav/version` and `:metav/tag`
  updated to reflect the git state after release. If the release performed a git
  push, the result of the push is found under the key `:metav.release/push-result`.
  If the release spited metadata, the paths of the spitted files can be found
  under the key `:metav.spit/spitted`.
  "
  [context]
  (let [context (utils/merge&validate context default-options ::release!-params)
        {:metav/keys [working-dir artefact-name version top-level]
         :metav.release/keys [level spit without-push]} context]
    (git/assert-committed? working-dir)
    (log/debug "execute!" context level)
    (log/debug "Current version of module '" artefact-name "' is:" (str version))

    (let [{bumped-version :metav/version
           bumped-tag     :metav/tag
           :as            bumped-context} (common/bump-context context)]

      (log/debug "Next version of module '" artefact-name "' is:" (str bumped-version))
      (log/debug "Next tag is" bumped-tag)

      ;;spit meta file and commit
      (let [bumped-context (when spit
                             (do-spits-and-commit! bumped-context))]

        (tag-repo! bumped-context)

        (cond-> bumped-context
                (not without-push)
                (assoc :metav.release/push-result (git/push! top-level)))))))


