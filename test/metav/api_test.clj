(ns metav.api-test
  (:require
    [clojure.test :refer [deftest testing]]
    [testit.core :refer [fact facts]]
    [metav.git-shell :as gs]
    [metav.version.protocols :as m-p]
    [metav.git :as m-git]
    [me.raynes.fs :as fs]
    [metav.api :as m-api]))


(defn sh [cmd]
  (println "-----------------")
  (println cmd)
  (println (:out (gs/sh cmd))))


(defn make-inner-repo
  ([name]
   (make-inner-repo name "0.0.0"))
  ([name version]
   (gs/mkdir-p! name)
   (gs/write-dummy-deps-edn-in! name)
   (gs/add!)
   (gs/commit!)
   (gs/tag! (str name "-" version))))

(defn make- [])


(defmacro with-repo [n & body]
  `(let [~n (gs/shell! (gs/init!))]
     (try
       ~@body
       (finally
         (fs/delete-dir ~n)))))


(defn recover-decision-infos [rep path]
  (let [f (str (fs/file rep path))
        ctxt (m-api/make-context f)
        v (:metav/version ctxt)]
    {:version v
     :distance (m-p/distance v)
     :artifact-name (:metav/artefact-name ctxt)}))

(comment
  (with-repo rep
    (let [print-infos #(let [info1 (recover-decision-infos rep "repo1")
                             info2 (recover-decision-infos rep "repo2")]
                         (println {:1 info1
                                   :2 info2}))]
      (gs/shell-in-dir! rep
        (make-inner-repo "repo1" "0.0.1")
        (make-inner-repo "repo2" "1.0.0")
        (sh "git status")
        (sh "git tag")
        (println "-----------------------------------")
        (println "repos are clean")
        (print-infos)

        (gs/write-dummy-file-in! "repo1" "1" "11")
        (gs/add!)
        (println "-----------------------------------")
        (println "repo1 is dirty")
        (print-infos)


        (gs/commit!)
        (println "-----------------------------------")
        (println "repo1 commited")
        (print-infos)

        (println "-----------------------------------")
        (sh "git status"))))

  (with-repo repo
    (gs/shell-in-dir! repo
      (gs/write-dummy-deps-edn-in!)
      (gs/add!)
      (gs/commit!)
      (sh "git status")
      (sh "git describe --long --match v*.* --abbrev=4 --dirty=-DIRTY --always")
      (println (m-api/make-context repo)))))


(deftest proper-naming
  (let [repo (gs/shell!
               (gs/init!)
               (gs/write-dummy-deps-edn-in!))]
    (gs/shell-in-dir! repo
      (make-inner-repo "repo1")
      (make-inner-repo "repo2")
      (sh "git status")
      (gs/add!)
      (sh "git status")
      (gs/commit!)
      (sh "git status --short"))
    (println (m-api/make-context (str (fs/file repo "repo1"))))
    (fs/delete-dir repo)))

