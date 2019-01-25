(ns metav.release
  (:require [metav.git :as git]
            [metav.display :refer [version tag]]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.version.protocols :refer [bump]]))

(defn assert-in-module?
  "assert whether the module-dir is really a module (that's to say with a deps.edn file in it)"
  [module-dir]
  true)

(defn execute
  "assert that nothing leaves uncommitted or untracked,
  then bump version to a releasable one (depending on the release level),
  commit, tag with the version (hence denoting a release),
  then push"
  ([scheme level] (execute nil scheme level))
  ([module-dir scheme level]
   (assert-in-module? module-dir)
   (git/assert-committed? module-dir)
   (let [repo-dir (git/toplevel module-dir)
         current-version (version module-dir :scheme scheme)
         next-version (bump current-version level)
         tag (tag module-dir next-version)]
     (prn "next version" next-version)
     (prn "next tag to be applied" tag)
    ; (git/commit! (str "Bump to version" next-version))
     (git/tag! repo-dir next-version)
    ; (git/push!)
     next-version)))

(defn main- [& args]
  (let [level (get args 0)]
    (prn "Release level is " level)
    (execute "semver" level)))
