(ns metav.release
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.tools.logging :as log :refer [debug]]
            [metav
             [git :as git :refer [assert-committed?]]
             [metadata :refer [invocation-context metadata-as-edn tag]]
             [release-cli :refer [accepted-levels exit validate-args]]
             [spit :as spit :refer [spit-files!]]]
            [metav.version.protocols :refer [bump]]))

(defn assert-in-module?
  "assert whether the module-dir is really a module (that's to say with a deps.edn file in it)"
  [module-dir]
  ;;TODO implement it :)
  true)

(defn assert-accepted-level? [level]
  (when-not (accepted-levels level) (throw (ex-info (str "Incorrect level: "level". Accepted levels are:" (string/join accepted-levels ", ")) {:accepted-levels accepted-levels :level level}))))

(defn execute!
  "assert that nothing leaves uncommitted or untracked,
  then bump version to a releasable one (depending on the release level),
  commit, tag with the version (hence denoting a release),
  then push
  return [module-name next-version tag push-result]"
  [{:keys [working-dir module-name version version-scheme without-push spit output-dir namespace formats template rendering-output without-sign] :as invocation-context} level]
  (assert-accepted-level? level)
  (assert-in-module? working-dir)
  (assert-committed? working-dir)
  (debug "execute!" invocation-context level)
  (debug "Current version of module '" module-name "' is:" (str version))
  (let [repo-dir (git/toplevel working-dir)
        next-version (bump version level)
        tag (tag working-dir module-name next-version)]
    (debug "Next version of module '" module-name "' is:" (str next-version))
    (debug "Next tag is" tag)
    ;;spit meta file and commit
    (when spit
      (let [spitted (spit-files! invocation-context next-version)]
        (apply git/add! working-dir spitted))) ;then tag
    (when template
      (let [rendered (spit/render! invocation-context next-version)]
        (apply git/add! working-dir rendered)))
    (when (or spit template)
      (git/commit! working-dir (str "Bump to version " next-version " and spit/render related metadata in file(s).")))
    (let [tag-result (apply git/tag! repo-dir tag (json/write-str (metadata-as-edn invocation-context next-version)) (when without-sign [:sign false]))]
      (if (int? (first tag-result));;error exit code if so return stderr
        (throw (Exception. (str "Error with git tag command:" (get tag-result 2))))))
    (if without-push
      [module-name next-version tag]
      (let [push-result (git/push! repo-dir)]
        [module-name next-version tag push-result]))))


(defn -main [& args]
  (let [{:keys [level options exit-message ok?] :as vargs} (validate-args args)
        {:keys [without-push spit output-dir namespace formats module-name-override] :as invocation-context} (invocation-context options)]
    (when exit-message (exit (if ok? 0 1) exit-message))
    (debug "Release level is " level ". Assert everything is committed, bump the version, tag and push.")
    (debug "Spitting metadata requested? " spit ". If true spit metadata (module-name, tag, version, sha, timestamp) in dir " output-dir " with namespace " namespace " and formats " formats)
    (let [[_ _ tag _] (execute! invocation-context level)]
      (if (:verbose options)
        (print (json/write-str (metadata-as-edn invocation-context (:version invocation-context))))
        (print tag))
      (shutdown-agents))))
