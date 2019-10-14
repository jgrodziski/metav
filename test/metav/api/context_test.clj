(ns metav.api.context-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [clojure.java.shell :as shell]
    [metav.git :as m-git]
    [metav.git-shell :as gs]
    [metav.version.protocols :as m-p]
    [me.raynes.fs :as fs]
    [metav.api.context :as m-ctxt]))


(defmacro with-repo [n & body]
  `(let [~n (gs/shell! (gs/init!))]
     (try
       ~@body
       (finally
         (fs/delete-dir ~n)))))

(defn version [repo-path]
  (let [ctxt (m-ctxt/make-context repo-path)]
    (-> ctxt :metav/version)))

(defn version-str [repo-path]
  (str (version repo-path)))


;;----------------------------------------------------------------------------------------------------------------------
;; Testing simple repo.
;;----------------------------------------------------------------------------------------------------------------------
(defmacro test-version [repo arrow test]
  `(fact (version-str ~repo) ~arrow ~test))

(deftest dedicated-repo-test
  (with-repo repo

             (testing "Metav won't work in a repo without any commits."
               (fact
                 (m-ctxt/make-context repo) =throws=> java.lang.Exception))

             (gs/shell-in-dir! repo
               (gs/commit!))

             (testing "Metav won't work in a dir without a build file."
               (fact
                 (m-ctxt/make-context repo) =throws=> java.lang.Exception))


             (gs/shell-in-dir! repo
               (gs/write-dummy-deps-edn-in!))

             (testing "testing initialized repo should return 0.1.0-xxxx and be non dirty"
               (let [v (version repo)]
                 (facts
                   (str v) =in=> #"0.1.0-*"
                   (m-p/dirty? v) => falsey)))


             (gs/shell-in-dir! repo
               (gs/write-dummy-file-in! "src")
               (gs/write-dummy-file-in! "src"))

             (testing "Untracked files in an initialized repo should not impact the version, the repo should be clean"
               (let [v (version repo)]
                 (facts
                   (str v) =in=> #"0.1.0-*"
                   (m-p/dirty? v) => falsey)))


             (gs/shell-in-dir! repo
               (gs/add!))

             (testing "When untracked files are added the repo should be dirty"
               (let [v (version repo)]
                 (facts
                   (m-p/dirty? v) => truthy)))


             (gs/shell-in-dir! repo
               (gs/commit!))

             (testing "When commited repo should be clean again."
               (let [v (version repo)]
                 (facts
                   (m-p/dirty? v) => falsey)))


             (gs/shell-in-dir! repo
               (gs/tag! "v1.2.0"))

             (testing "Version after tagging."
               (test-version repo => "1.2.0"))


             (gs/shell-in-dir! repo
               (gs/write-dummy-file-in! "src")
               (gs/add!))

             (testing "testing a file add should give dirty"
               (test-version repo => "1.2.0-DIRTY"))


             (gs/shell-in-dir! repo
               (gs/commit!)
               (gs/write-dummy-file-in! "src")
               (gs/add!)
               (gs/commit!))

             (testing "correct distance"
               (let [ctxt (m-ctxt/make-context repo)
                     version (:metav/version ctxt)
                     distance (m-p/distance version)]
                 (facts
                   (str version) =in=> #"1.2.0-*"
                   distance => 2)))


             (testing "Correct naming"
               (let [ctxt (m-ctxt/make-context repo)
                     ctxt-full-name (m-ctxt/make-context repo {:metav/use-full-name? true})
                     ctxt-other-name (m-ctxt/make-context repo {:metav/module-name "another-name"})
                     ctxt-other-full-name (m-ctxt/make-context repo {:metav/use-full-name? true
                                                                     :metav/module-name     "another-name"})]
                 (facts
                   ctxt                 =in=> {:metav/artefact-name (fs/base-name repo)}
                   ctxt-full-name       =in=> {:metav/artefact-name (fs/base-name repo)}
                   ctxt-other-name      =in=> {:metav/artefact-name "another-name"}
                   ctxt-other-full-name =in=> {:metav/artefact-name "another-name"})))))


;;----------------------------------------------------------------------------------------------------------------------
;; Testing Monorepo
;;----------------------------------------------------------------------------------------------------------------------
(defn make-project! [{:keys [name version full-name?]}]
  (let [repo-name (fs/base-name shell/*sh-dir*)
        full-name (str repo-name "-" name)
        tag-name (if full-name? full-name name)
        tag (str tag-name "-" version)]
    (gs/write-dummy-deps-edn-in! name)
    (gs/write-dummy-file-in! name "src")
    (gs/add!)
    (gs/commit!)
    (gs/tag! tag)))


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


(defmacro with-mono-repo [n & body]
  `(let [~n (make-monorepo!)]
     (try
       ~@body
       (finally
         (delete-monorepo ~n)))))


(deftest monorepo-test
  (with-mono-repo m
                  (let [{:keys [remote monorepo modules] :as mono} m
                        {project1 :p1
                         project2 :p2
                         moduleA1 :A1
                         moduleA2 :A2
                         moduleB1 :B1
                         moduleB2 :B2
                         moduleB3 :B3} modules]
                    (testing "The root of the monorepo doesn't have a build file. exception thrown."
                      (facts
                        (m-ctxt/make-context monorepo) =throws=> Exception))

                    (testing "Versions are set correctly."
                      (test-version project1 =in=> #"^0.0.0")
                      (test-version project2 =in=> #"^0.1.0")
                      (test-version moduleA1 =in=> #"^1.3.4")
                      (test-version moduleA2 =in=> #"^1.1.2")
                      (test-version moduleB1 =in=> #"^1.2.0")
                      (test-version moduleB2 =in=> #"^1.5.7")
                      (test-version moduleB3 =in=> #"^0.1.0"))

                    (testing "Names are properly read."
                      (let [ctxt1 (m-ctxt/make-context project1)
                            ctxt2 (m-ctxt/make-context project2 {:metav/use-full-name? true})
                            ctxtA1 (m-ctxt/make-context moduleA1)
                            ctxtA2 (m-ctxt/make-context moduleA2)
                            ctxtB1 (m-ctxt/make-context moduleB1)
                            ctxtB2 (m-ctxt/make-context moduleB2)
                            ctxtB3 (m-ctxt/make-context moduleB3)
                            expected-project2-name (str (fs/base-name monorepo) "-project2")]
                        (facts
                          (:metav/artefact-name ctxt2) => expected-project2-name
                          (:metav/version-prefix ctxt2)  => (str expected-project2-name "-")

                          ctxt1 =in=> #:metav{:artefact-name "project1"
                                              :metav/version-prefix "project1-"}

                          ctxtA1 =in=> #:metav{:artefact-name "sysA-container1"
                                               :metav/version-prefix "sysA-container1-"}

                          ctxtA2 =in=> #:metav{:artefact-name "sysA-container2"
                                               :metav/version-prefix "sysA-container2-"}

                          ctxtB1 =in=> #:metav{:artefact-name "sysB-container1"
                                               :metav/version-prefix "sysB-container1-"}

                          ctxtB2 =in=> #:metav{:artefact-name "sysB-container2"
                                               :metav/version-prefix "sysB-container2-"}

                          ctxtB3 =in=> #:metav{:artefact-name "sysB-container3"
                                               :metav/version-prefix "sysB-container3-"}))))))
