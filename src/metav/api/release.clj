(ns metav.api.release
  (:require
    [clojure.spec.alpha :as s]
    [clojure.tools.logging :as log]
    [clojure.data.json :as json]
    [clojure.set :refer [union]]
    [metav.git :as m-git]
    [metav.api.common :as m-a-c]
    [metav.api.spit :as m-spit]
    [metav.version.semver :as m-semver]
    [metav.version.maven :as m-maven]))

;;----------------------------------------------------------------------------------------------------------------------
;; Release conf
;;----------------------------------------------------------------------------------------------------------------------
(def default-options
  #:metav.release{:level :patch
                  :without-sign false
                  :spit false
                  :without-push false})

(s/def :metav.release/level (union m-semver/allowed-bumps m-maven/allowed-bumps))
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
        ctxt-with-spits (m-spit/perform! bumped-context)]
    (apply m-git/add! working-dir (:metav.spit/spitted ctxt-with-spits))
    (m-git/commit! working-dir
                   (format "Bump module %s to version %s and spit/render related metadata in file(s)." artefact-name version))
    ctxt-with-spits))

(defn tag-repo! [bumped-context]
  (let [{:metav/keys  [top-level tag]
         :metav.release/keys [without-sign]} bumped-context
        tag-result (apply m-git/tag! top-level
                          tag
                          (json/write-str (m-a-c/metadata-as-edn bumped-context))
                          (when without-sign [:sign false]))]
    (if (int? (first tag-result)) ;;error exit code if so return stderr
      (throw (Exception. (str "Error with git tag command:" (get tag-result 2)))))))


(defn perform*!
  "assert that nothing leaves uncommitted or untracked,
  then bump version to a releasable one (depending on the release level),
  commit, tag with the version (hence denoting a release),
  then push
  return [module-name next-version tag push-result]"
  [context]
  (let [{:metav/keys [working-dir artefact-name version top-level]
         :metav.release/keys [level spit without-push]} context]
    (m-git/assert-committed? working-dir)
    (log/debug "execute!" context level)
    (log/debug "Current version of module '" artefact-name "' is:" (str version))

    (let [{bumped-version :metav/version
           bumped-tag     :metav/tag
           :as            bumped-context} (m-a-c/bump-context context)]

      (log/debug "Next version of module '" artefact-name "' is:" (str bumped-version))
      (log/debug "Next tag is" bumped-tag)

      ;;spit meta file and commit
      (let [bumped-context (when spit
                             (do-spits-and-commit! bumped-context))]

        (tag-repo! bumped-context)

        (cond-> bumped-context
                (not without-push)
                (assoc :metav.release/push-result (m-git/push! top-level)))))))


(defn bump-level-valid? [context]
  (let [{scheme :metav/version-scheme
         level :metav.release/level} context
        spec (if (= :semver scheme)
               ::m-semver/accepted-bumps
               ::m-maven/accepted-bumps)]
    (s/valid? spec level)))

(s/def :metav.release/param (s/keys :req [:metav.release/level]))


(defn perform! [context]
  (s/assert (s/and :metav.release/param
                   :metav.release/options
                   bump-level-valid?)
            context)
  (perform*! context))