(ns metav.domain.leningen
  (:require
    [clojure.string :as string]
    [clojure.tools.logging :as log]))

(defn sync-lein-version!
  "Change version in project.clj file for leningen project.
  Takes the bumped version as parameter.

  Note : Due to regex, we can't read project through clojure.edn/read-string. Replace version by string manipulation instead."
  [context]
  (let [{:metav/keys [working-dir version]} context
        bumped-version (.toString version)
        project-file-path (str working-dir "/project.clj")
        content (slurp project-file-path)
        [defproject+group-artifact+version group-id artifact-name version-to-bump] (re-find #"\(defproject\ ((?:\w|\.|\-)+)\/((?:\w|\/|\-)+) \"([^\"]*)\"" content)
        bumped-content (string/replace content defproject+group-artifact+version (string/replace defproject+group-artifact+version version-to-bump bumped-version))]
    (log/debug (str "Bump lein project.clj version in " project-file-path " from " version-to-bump " to " bumped-version))
    (spit project-file-path bumped-content)
    (log/debug (str "Lein project.clj version bumped in " project-file-path " from " version-to-bump " to " bumped-version))
    {:metav.lein/group-id          group-id
     :metav.lein/artifact-name     artifact-name
     :metav.lein/previous-version  version-to-bump
     :metav.lein/version           bumped-version
     :metav.lein/project-file-path "project.clj"}))