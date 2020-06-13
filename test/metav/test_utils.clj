(ns metav.test-utils
  (:require
    [clojure.java.shell :as shell]
    [me.raynes.fs :as fs]
    [metav.git-shell :as gs]
    [metav.api :as api]))


(defn make-context
  ([path]
   (api/make-context {:metav/working-dir path}))
  ([path opts]
   (api/make-context (assoc opts :metav/working-dir path))))

(defmacro with-repo [repo-dir & body]
  `(let [~repo-dir   (gs/shell! (gs/init!))]
    (try
      (gs/shell-in-dir! ~repo-dir
                        (gs/checkout! "master")
                        (let [remote-dir# (gs/shell! (gs/clone! ~repo-dir (gs/repo-temp-dir)))]
                          (gs/remote-add! "origin" (str remote-dir# "/.git"))
                          ~@body
                          (fs/delete-dir remote-dir#))
                        )
      (finally
        (fs/delete-dir ~repo-dir)
        ))))

;;----------------------------------------------------------------------------------------------------------------------
;; Dedicated repo stuff
;;----------------------------------------------------------------------------------------------------------------------
(defn prepare-base-repo! [path]
  (gs/shell-in-dir! path
    (gs/write-dummy-deps-edn-in!)
    (gs/sh "mkdir resources")
    (gs/add!)
    (gs/commit!)
    (gs/tag! "v0.1.0")))

(defn make-standalone-project! [dir version tag]
  (gs/write-dummy-deps-edn-in! dir)
  (gs/write-dummy-file-in! dir "src")
  (gs/add!)
  (gs/commit!)
  (gs/tag! tag))

(defn make-project! [{:keys [name version full-name?]}]
  (let [repo-name (fs/base-name shell/*sh-dir*)
        full-name (str repo-name "-" name)
        tag-name  (if full-name? full-name name)
        tag       (str tag-name "-" version)]
    (gs/write-dummy-deps-edn-in! name)
    (gs/write-dummy-file-in! name "src")
    (gs/add!)
    (gs/commit!)
    (gs/tag! tag)))

(def project1-version "0.0.0")
(def project2-version "1.1.2")
(def sysA-c1-version "1.3.4")
(def sysA-c2-version "1.1.2")
(def sysB-c1-version "1.2.0")
(def sysB-c2-version "1.5.7")
(def sysB-c3-version "0.1.0")


(defn make-repo! []
  (let [remote (gs/shell! (gs/init-bare!))
        repo (gs/shell!
              (gs/clone! remote)
              (make-standalone-project! "standalone-project" "1.2.3" "v1.2.3")

              (gs/write-dummy-file-in! "src" "com" "company" "domain")
              (gs/add!)
              (gs/commit!)
              (gs/tag! "v1.2.4")

              (gs/write-dummy-file-in! "src" "com" "company" "domain")

              (gs/write-dummy-file-in! "sysA" "container2" "src")
              (gs/add!)
              (gs/commit!)
              (gs/tag! "v1.2.5"))]
    {:remote remote
     :repo   repo}))

;;----------------------------------------------------------------------------------------------------------------------
;; Monorepo stuff
;;----------------------------------------------------------------------------------------------------------------------

(defn make-monorepo! []
  (let [remote (gs/shell! (gs/init-bare!))
        monorepo (gs/shell!
                   (gs/clone! remote)

                   (make-project! {:name "project1" :version "0.0.0"})

                   (make-project! {:name "project2" :version "1.1.2" :full-name? true})

                   (gs/write-dummy-deps-edn-in! "sysA" "container1")
                   (gs/write-dummy-file-in! "sysA" "container1" "src")
                   (gs/add!)
                   (gs/commit!)
                   (gs/tag! "sysA-container1-1.3.4")

                   (gs/write-dummy-deps-edn-in! "sysA" "container2")
                   (gs/write-dummy-file-in! "sysA" "container2" "src")
                   (gs/add!)
                   (gs/commit!)
                   (gs/tag! "sysA-container2-1.1.2")

                   (gs/write-dummy-deps-edn-in! "sysB" "container1")
                   (gs/write-dummy-file-in! "sysB" "container1" "src")
                   (gs/add!)
                   (gs/commit!)
                   (gs/tag! "sysB-container1-1.2.0")

                   (gs/write-dummy-deps-edn-in! "sysB" "container2")
                   (gs/write-dummy-file-in! "sysB" "container2" "src")
                   (gs/add!)
                   (gs/commit!)

                   (gs/tag! "sysB-container2-1.5.7")
                   (gs/write-dummy-deps-edn-in! "sysB" "container3")
                   (gs/write-dummy-file-in! "sysB" "container3" "src")
                   (gs/add!)
                   (gs/commit!))

        project1 (str (fs/file monorepo "project1"))
        project2 (str (fs/file monorepo "project2"))
        moduleA1 (str (fs/file monorepo "sysA" "container1"))
        moduleA2 (str (fs/file monorepo "sysA" "container2"))
        moduleB1 (str (fs/file monorepo "sysB" "container1"))
        moduleB2 (str (fs/file monorepo "sysB" "container2"))
        moduleB3 (str (fs/file monorepo "sysB" "container3"))]
    {:remote remote
     :monorepo monorepo
     :modules {:p1 project1
               :p2 project2
               :A1 moduleA1
               :A2 moduleA2
               :B1 moduleB1
               :B2 moduleB2
               :B3 moduleB3}}))


(defn delete-monorepo [r]
  (let [{:keys [remote monorepo]} r]
    (fs/delete-dir remote)
    (fs/delete-dir monorepo)))


(defmacro with-example-monorepo [n & body]
  `(let [~n (make-monorepo!)]
     (try
       ~@body
       (finally
         (delete-monorepo ~n)))))
