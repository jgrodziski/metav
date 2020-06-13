(ns metav.domain.release
  (:require
   [clojure.spec.alpha          :as s]
   [clojure.tools.logging       :as log]
   [clojure.set :refer [union]]
   [metav.utils                 :as utils]
   [metav.domain.context        :as context]
   [metav.domain.git-operations :as git-ops]
   [metav.domain.pom            :as pom]
   [metav.domain.spit           :as spit]
   [metav.domain.version.common :as version]
   [metav.domain.version.semver :as semver]
   [metav.domain.version.maven  :as maven]))


;;----------------------------------------------------------------------------------------------------------------------
;; Release conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  #:metav.release{:level        :patch
                  :spit         false
                  :pom          false
                  :without-push false})

(s/def :metav.release/level (union semver/allowed-bumps maven/allowed-bumps))
(s/def :metav.release/spit boolean?)
(s/def :metav.release/pom boolean?)
(s/def :metav.release/without-push boolean?)


(s/def :metav.release/options
  (s/keys :opt [:metav.release/level
                :metav.release/without-sign
                :metav.release/spit
                :metav.release/without-push]))


(defn bump-level-valid? [context]
  (let [{scheme :metav/version-scheme
         level :metav.release/level} context
        spec (if (= :semver scheme)
               ::semver/accepted-bumps
               ::maven/accepted-bumps)]
    (s/valid? spec level)))


(defn new-version [{current-version :metav/version
                    level           :metav.release/level
                    :as context}]
  (version/bump current-version level))


(s/def ::bump-context-param (s/and (s/merge :metav/context
                                            (s/keys :req [:metav.release/level]))
                                   bump-level-valid?))

(defn bump-version [context]
  (utils/check-spec ::bump-context-param context)
  (assoc context :metav/version (new-version context)))

(defn tag [context]
  (assoc context :metav/tag (context/tag (:metav/version-prefix context) (:metav/version context))))

(defn log-before-bump [context]
  (let [{:metav/keys [artefact-name version]
         level :metav.release/level} context]
    (log/debug "execute!" context level)
    (log/debug "Current version of module '" artefact-name "' is:" (str version))))


(defn log-bumped-data [context]
  (let [{artefact-name  :metav/artefact-name
         bumped-version :metav/version
         bumped-tag     :metav/tag} context]
    (log/debug "Next version of module '" artefact-name "' is:" (str bumped-version))
    (log/debug "Next tag is" bumped-tag)))


(defn do-spits-and-commit! [bumped-context]
  (let [{:metav/keys [artefact-name version]} bumped-context]
    (-> bumped-context
        (spit/spit!)
        (spit/git-add-spitted!)
        (git-ops/commit! (format "Bump %s to %s and spit metadata" artefact-name version)))))


(defn maybe-spit! [context]
  (let [spit? (:metav.release/spit context)]
    (cond-> context
            spit? do-spits-and-commit!)))

(defn maybe-push! [context]
  (let [without-push? (:metav.release/without-push context)]
    (cond-> context
            (not without-push?) git-ops/push!)))


(s/def ::release!-params (s/and (s/merge :metav/context
                                         :metav.release/options)
                                bump-level-valid?))


(defn release!
  "Assert that nothing leaves uncommitted or untracked,
  then bump version to a releasable one (depending on the release level),
  commit, tag with the version (hence denoting a release), then push.

  Returns the context passed as parameter with the keys `:metav/version` and `:metav/tag`
  updated to reflect the git state after release. If the release performed a git
  push, metav push the commits and tags in two successive push, the result of the pushs are found under the key `:metav.release/push-results`.
  If the release spited metadata, the paths of the spitted files can be found
  under the key `:metav.spit/spitted`.
  "
  [context]

  (-> context
      (utils/merge&validate default-options ::release!-params)
      git-ops/check-committed?

      (utils/side-effect-from-context! log-before-bump)
      bump-version
      tag
      (utils/side-effect-from-context! log-bumped-data)

      maybe-spit!
      git-ops/tag-repo!
      maybe-push!))


