(ns metav.release
  (:require [metav.git :as git]
            [metav.display :refer [version]]
            [metav.version.protocols :refer [bump]]))

(defn release
  "assert that anything leaves uncommitted or untracked, then bump version to a releasable one, commit, tag the release version, then push"
  ([scheme level] (release nil scheme level))
  ([repo-dir scheme level]
   (git/assert-committed? repo-dir)
   (let [current-version (version scheme repo-dir)
         next-version (bump current-version level)]
     (git/commit! (str "Bump to version" next-version))
     (git/tag! next-version)
     (git/push!))))

(defn main- [& args]
  (let [level (get args 0)]
    (release "semver" level)))
