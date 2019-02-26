(ns metav.release
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [metav.git :as git]
            [metav.metadata :refer [tag invocation-context metadata-as-edn]]
            [metav.spit :as spit :refer [spit-files!]]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.version.protocols :refer [bump]]
            [metav.release-cli :refer [validate-args accepted-levels exit]]
            [clojure.data.json :as json]
            [clojure.set :as set]))

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
  [{:keys [working-dir module-name version version-scheme spit output-dir namespace formats] :as invocation-context} level]
  (when-not (accepted-levels level) (throw (Exception. (str "Incorrect level: "level". Accepted levels are:" (string/join accepted-levels ", ")))))
  (log/debug "execute!" invocation-context level)
  (assert-in-module? working-dir)
  (git/assert-committed? working-dir)
  (let [repo-dir (git/toplevel working-dir)
        next-version (bump version level)
        tag (tag working-dir module-name next-version)]
    (log/debug "Current version of module '" module-name "' is:" (str version))
    (log/debug "Next version of module '" module-name "' is:" (str next-version))
    (log/debug "Next tag is" tag)
    ;;spit meta file and commit
    (when spit
      (let [spitted (spit-files! invocation-context next-version)]
        (apply git/add! working-dir spitted)
        (git/commit! working-dir (str "Bump to version " next-version " and spit related metadata in file(s)."))))
                                        ;then tag
    (git/tag! repo-dir tag (json/write-str (metadata-as-edn invocation-context)))
    (let [push-result (git/push! repo-dir)]
      [module-name next-version tag push-result])))


(defn -main [& args]
  (let [{:keys [level options exit-message ok?] :as vargs} (validate-args args)
        {:keys [spit output-dir namespace formats module-name-override] :as invocation-context} (invocation-context options)]
    (when exit-message
      (exit (if ok? 0 1) exit-message))
    (log/debug "Release level is " level ". Assert everything is committed, bump the version, tag and push.")
    (log/debug "Spitting metadata requested? " spit ". If true spit metadata (module-name, tag, version, sha, timestamp) in dir " output-dir " with namespace " namespace " and formats " formats)
    (let [[_ _ tag _] (execute! invocation-context level)]
      (if (:verbose options)
        (print (json/write-str (metadata-as-edn invocation-context)))
        (print tag))
      (shutdown-agents))))
