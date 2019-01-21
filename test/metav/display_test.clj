(ns metav.display-test
  (:require [clojure.test :refer :all]
            [testit.core :refer :all]
            [metav.git-shell :refer :all]
            [metav.display :refer :all]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.git :as git]
            [me.raynes.fs :as fs]))

(defn setup-tear-down [test-to-run]
  (test-to-run))

(use-fixtures :once setup-tear-down)

(deftest dedicated-repo-with-semver
  (testing "testing initialized repo should return 0.1.0"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!))]
      (facts (str (version "semver" repo)) => "0.1.0"
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing untracked file should not impact the version"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111"))]
      (facts (str (version "semver" repo)) => "0.1.0"
            (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing added should be dirty"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111") (add!))]
      (facts (str (version "semver" repo)) => "0.1.0"
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing committed"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111")
                       (add!) (commit!))]
      (facts (str (version "semver" repo)) =in=> #"0.1.1+.*"
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing tagged"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111")
                       (add!) (commit!) (tag! "v1.3.0"))]
      (facts (str (version "semver" repo)) => "1.3.0"
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing a file add should give dirty"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111")
                       (add!) (commit!) (tag! "v1.3.0")
                       (write-dummy-file-in! "2" "22" "222") (add!))]
      (facts (str (version "semver" repo)) => "1.3.0+DIRTY")
      (fs/delete-dir repo)))
  (testing "testing 2 commits should increase the patch number by 2"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!) (tag! "v1.3.0")
                       (write-dummy-file-in! "2" "22" "222") (add!) (commit!)
                       (write-dummy-file-in! "3" "33" "333") (add!) (commit!))]
      (facts (str (version "semver" repo)) =in=> #"1.3.2+.*")
      (fs/delete-dir repo)))
  (testing "testing tagged"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!) (tag! "v1.3.0"))]
      (facts (str (version "semver" repo)) => "1.3.0" )
      (fs/delete-dir repo))))

(defn- monorepo []
  (let [monorepo (shell! (init!)
                         (write-dummy-deps-edn-in! "sysA" "container1")
                         (add!)
                         (commit!)
                         (tag! "sysA-container1-1.2.3")
                         (write-dummy-deps-edn-in! "sysA" "container2")
                         (add!)
                         (commit!)
                         (tag! "sysA-container2-1.2.3")
                         (write-dummy-deps-edn-in! "sysB" "container1")
                         (add!)
                         (commit!)
                         (tag! "sysB-container1-1.2.3")
                         (write-dummy-deps-edn-in! "sysB" "container2")
                         (add!)
                         (commit!)
                         (tag! "sysB-container2-1.2.3"))
        moduleA1 (str monorepo "/sysA/container1")
        moduleA2 (str monorepo "/sysA/container2")
        moduleB1 (str monorepo "/sysB/container1")
        moduleB2 (str monorepo "/sysB/container2")]
    [monorepo moduleA1 moduleA2 moduleB1 moduleB2]))

(deftest monorepo-with-semver
  (testing "testing a monorepo with two systems of two containers"
    (let [[monorepo moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)]
      (facts
       (monorepo? monorepo) => truthy
       (monorepo? moduleA1) => truthy
       (monorepo? moduleA2) => truthy
       (monorepo? moduleB1) => truthy
       (monorepo? moduleB2) => truthy
       (str (module-name moduleA1) (version "semver" moduleA1)) =in=> #"sysA-container1-1.2.3.*")
      (fs/delete-dir monorepo)))
  (testing "testing a monorepo with two systems of two containers"
    (let [[monorepo moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)]

      (fs/delete-dir monorepo)))
  (testing "testing a monorepo with two systems of two containers"
    (let [[monorepo moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)]

      (fs/delete-dir monorepo))))
