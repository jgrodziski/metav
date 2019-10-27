(ns metav.domain.context-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [me.raynes.fs :as fs]
    [metav.domain.version.protocols :as version]
    [metav.git-shell :as git-shell]
    [metav.utils-test :as utils-test]
    ))


(defn version [repo-path]
  (let [ctxt (utils-test/make-context repo-path)]
    (-> ctxt :metav/version)))

(defn version-str [repo-path]
  (str (version repo-path)))



;;----------------------------------------------------------------------------------------------------------------------
;; Testing simple repo.
;;----------------------------------------------------------------------------------------------------------------------
(defmacro test-version [repo arrow test]
  `(fact (version-str ~repo) ~arrow ~test))

(deftest dedicated-repo-test
  (utils-test/with-repo repo

    (testing "Metav won't work in a repo without any commits."
      (fact
        (utils-test/make-context repo) =throws=> java.lang.Exception))

    (git-shell/shell-in-dir! repo
      (git-shell/commit!))

    (testing "Metav won't work in a dir without a build file."
      (fact
        (utils-test/make-context repo) =throws=> java.lang.Exception))


    (git-shell/shell-in-dir! repo
      (git-shell/write-dummy-deps-edn-in!))

    (testing "testing initialized repo should return 0.1.0-xxxx and be non dirty"
      (let [v (version repo)]
        (facts
          (str v) =in=> #"0.1.0-*"
          (version/dirty? v) => falsey)))


    (git-shell/shell-in-dir! repo
      (git-shell/write-dummy-file-in! "src")
      (git-shell/write-dummy-file-in! "src"))

    (testing "Untracked files in an initialized repo should not impact the version, the repo should be clean"
      (let [v (version repo)]
        (facts
          (str v) =in=> #"0.1.0-*"
          (version/dirty? v) => falsey)))


    (git-shell/shell-in-dir! repo
      (git-shell/add!))

    (testing "When untracked files are added the repo should be dirty"
      (let [v (version repo)]
        (facts
          (version/dirty? v) => truthy)))


    (git-shell/shell-in-dir! repo
      (git-shell/commit!))

    (testing "When commited repo should be clean again."
      (let [v (version repo)]
        (facts
          (version/dirty? v) => falsey)))


    (git-shell/shell-in-dir! repo
      (git-shell/tag! "v1.2.0"))

    (testing "Version after tagging."
      (test-version repo => "1.2.0"))


    (git-shell/shell-in-dir! repo
      (git-shell/write-dummy-file-in! "src")
      (git-shell/add!))

    (testing "testing a file add should give dirty"
      (test-version repo => "1.2.0-DIRTY"))


    (git-shell/shell-in-dir! repo
      (git-shell/commit!)
      (git-shell/write-dummy-file-in! "src")
      (git-shell/add!)
      (git-shell/commit!))

    (testing "correct distance"
      (let [ctxt (utils-test/make-context repo)
            version (:metav/version ctxt)
            distance (version/distance version)]
        (facts
          (str version) =in=> #"1.2.0-*"
          distance => 2)))


    (testing "Correct naming"
      (let [ctxt (utils-test/make-context repo)
            ctxt-full-name (utils-test/make-context repo {:metav/use-full-name? true})
            ctxt-other-name (utils-test/make-context repo {:metav/module-name-override "another-name"})
            ctxt-other-full-name (utils-test/make-context repo {:metav/use-full-name? true
                                                        :metav/module-name-override    "another-name"})]
        (facts
          ctxt =in=> {:metav/artefact-name (fs/base-name repo)}
          ctxt-full-name =in=> {:metav/artefact-name (fs/base-name repo)}
          ctxt-other-name =in=> {:metav/artefact-name "another-name"}
          ctxt-other-full-name =in=> {:metav/artefact-name "another-name"})))))


;;----------------------------------------------------------------------------------------------------------------------
;; Testing Monorepo
;;----------------------------------------------------------------------------------------------------------------------
(def v-str #(-> % :metav/version str))
(def str->v-regex #(->> % (str "^") re-pattern))

(deftest monorepo-test
  (utils-test/with-example-monorepo m
    (let [{:keys [remote monorepo modules] :as mono} m
          {project1 :p1
           project2 :p2
           moduleA1 :A1
           moduleA2 :A2
           moduleB1 :B1
           moduleB2 :B2
           moduleB3 :B3} modules

          ctxt1 (utils-test/make-context project1)
          ctxt2 (utils-test/make-context project2 {:metav/use-full-name? true})
          ctxtA1 (utils-test/make-context moduleA1)
          ctxtA2 (utils-test/make-context moduleA2)
          ctxtB1 (utils-test/make-context moduleB1)
          ctxtB2 (utils-test/make-context moduleB2)
          ctxtB3 (utils-test/make-context moduleB3)
          expected-project2-name (str (fs/base-name monorepo) "-project2")]
      (testing "The root of the monorepo doesn't have a build file. Exception thrown."
        (facts
          (utils-test/make-context monorepo) =throws=> Exception))

      (testing "Versions are set correctly."
        (facts
          (v-str ctxt1) =in=> (str->v-regex utils-test/project1-version)
          (v-str ctxt2) =in=> (str->v-regex utils-test/project2-version)
          (v-str ctxtA1) =in=> (str->v-regex utils-test/sysA-c1-version)
          (v-str ctxtA2) =in=> (str->v-regex utils-test/sysA-c2-version)
          (v-str ctxtB1) =in=> (str->v-regex utils-test/sysB-c1-version)
          (v-str ctxtB2) =in=> (str->v-regex utils-test/sysB-c2-version)
          (v-str ctxtB3) =in=> (str->v-regex utils-test/sysB-c3-version)))

      (testing "Names are properly read."
        (facts
          (:metav/artefact-name ctxt2) => expected-project2-name
          (:metav/version-prefix ctxt2) => (str expected-project2-name "-")

          ctxt1 =in=> #:metav{:artefact-name        "project1"
                              :metav/version-prefix "project1-"}

          ctxtA1 =in=> #:metav{:artefact-name        "sysA-container1"
                               :metav/version-prefix "sysA-container1-"}

          ctxtA2 =in=> #:metav{:artefact-name        "sysA-container2"
                               :metav/version-prefix "sysA-container2-"}

          ctxtB1 =in=> #:metav{:artefact-name        "sysB-container1"
                               :metav/version-prefix "sysB-container1-"}

          ctxtB2 =in=> #:metav{:artefact-name        "sysB-container2"
                               :metav/version-prefix "sysB-container2-"}

          ctxtB3 =in=> #:metav{:artefact-name        "sysB-container3"
                               :metav/version-prefix "sysB-container3-"})))))
