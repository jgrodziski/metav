(ns metav.release
  (:require [clojure.tools.logging :as log]
            [metav.git :as git]
            [metav.display :refer [version tag module-name]]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.version.protocols :refer [bump]]))

(defn assert-in-module?
  "assert whether the module-dir is really a module (that's to say with a deps.edn file in it)"
  [module-dir]
  ;;TODO implement it :)
  true)

(defn execute!
  "assert that nothing leaves uncommitted or untracked,
  then bump version to a releasable one (depending on the release level),
  commit, tag with the version (hence denoting a release),
  then push
  return [module-name next-version tag push-result]"
  ([scheme level] (execute! nil scheme level))
  ([module-dir scheme level]
   (assert-in-module? module-dir)
   (git/assert-committed? module-dir)
   (let [repo-dir (git/toplevel module-dir)
         module-name (module-name module-dir)
         current-version (version module-dir :scheme scheme)
         next-version (bump current-version level)
         tag (tag module-dir next-version)]
     (log/info "Next version for module " module-name " is: " (str next-version))
     (log/info "Next tag is " tag)
    ; (git/commit! (str "Bump to version" next-version))
     (git/tag! repo-dir tag)
     (let [push-result (git/push! repo-dir)]
       [module-name next-version tag push-result]))))

(defn main- [& args]
  (let [level (get args 0)]
    (log/debug "Release level is " level ". Assert everything is committed, bump the version, tag and push.")
    (let [[module-name next-version tag push-result] (execute! (git/pwd) "semver" (keyword level))]
      (pr tag))))
