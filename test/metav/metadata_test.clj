(ns metav.metadata-test
  (:require [clojure.test :refer :all]
            [testit.core :refer :all]
            [metav.git-shell :refer :all]
            [metav.metadata :refer [version module-name artefact-name tag]]
            [metav.repo :refer [monorepo? dedicated-repo?]]
            [metav.git :as git]
            [me.raynes.fs :as fs]))

(defn- remove-newline [s]
  (clojure.string/replace s "\n" ""))

(defn dedicated-repo-tagged-then-2-commits [v]
  (shell!
   (let [pwd (remove-newline (:out (sh "pwd")))]
     (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!)
     (tag! (tag pwd "dummy-module-name" v))
     (write-dummy-file-in! "2" "22" "222") (add!) (commit!)
     (write-dummy-file-in! "3" "33" "333") (add!) (commit!))))

(defn dedicated-repo-tagged []
  (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!) (tag! "v1.3.0")))

(deftest dedicated-repo-with-semver
  (testing "testing initialized repo should return 0.1.0"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!))]
      (facts (str (version repo "dummy-module-name")) => "0.1.0"
             (monorepo? repo) => falsey
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing untracked file should not impact the version"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111"))]
      (facts (str (version repo "dummy-module-name")) => "0.1.0"
            (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing added should be dirty"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111") (add!))]
      (facts (str (version repo "dummy-module-name")) => "0.1.0"
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing committed"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111")
                       (add!) (commit!))]
      (facts (str (version repo "dummy-module-name")) =in=> #"0.1.0+.*"
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing tagged"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111")
                       (add!) (commit!) (tag! "v1.3.0"))]
      (facts (str (version repo "dummy-module-name")) => "1.3.0"
             (monorepo? repo) => falsey
             (dedicated-repo? repo) => true)
      (fs/delete-dir repo)))
  (testing "testing a file add should give dirty"
    (let [repo (shell! (init!) (write-dummy-deps-edn-in!)
                       (write-dummy-file-in! "1" "11" "111")
                       (add!) (commit!) (tag! "v1.3.0")
                       (write-dummy-file-in! "2" "22" "222") (add!))]
      (facts (str (version repo "dummy-module-name")) => "1.3.0+DIRTY")
      (fs/delete-dir repo)))
  (testing "testing 2 commits should increase the patch number by 2"
    (let [repo (dedicated-repo-tagged-then-2-commits "1.3.0")]
      (facts (str (version repo "dummy-module-name")) =in=> #"1.3.0+.*")
      (fs/delete-dir repo)))
  (testing "testing tagged"
    (let [repo (dedicated-repo-tagged)]
      (facts (str (version repo "dummy-module-name")) => "1.3.0" )
      (fs/delete-dir repo))))

(defn monorepo []
  (let [remote (shell! (init-bare!))
        monorepo (shell! (clone! remote)
                         (write-dummy-deps-edn-in! "sysA" "container1")
                         (write-dummy-file-in! "sysA" "container1" "src")
                         (add!)
                         (commit!)
                         (tag! "sysA-container1-1.3.4")
                         (write-dummy-deps-edn-in! "sysA" "container2")
                         (write-dummy-file-in! "sysA" "container2" "src")
                         (add!)
                         (commit!)
                         (tag! "sysA-container2-1.1.2")
                         (write-dummy-deps-edn-in! "sysB" "container1")
                         (write-dummy-file-in! "sysB" "container1" "src")
                         (add!)
                         (commit!)
                         (tag! "sysB-container1-1.2.0")
                         (write-dummy-deps-edn-in! "sysB" "container2")
                         (write-dummy-file-in! "sysB" "container2" "src")
                         (add!)
                         (commit!)
                         (tag! "sysB-container2-1.5.7"))
        moduleA1 (str monorepo "/sysA/container1")
        moduleA2 (str monorepo "/sysA/container2")
        moduleB1 (str monorepo "/sysB/container1")
        moduleB2 (str monorepo "/sysB/container2")]
    [monorepo remote moduleA1 moduleA2 moduleB1 moduleB2]))

(deftest tag-name
  (testing "a monorepo should prefix the tag with the module-name"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)]
      (facts
       (tag moduleA1 "moduleA1-name" "1.3.4") => "moduleA1-name-1.3.4"
       (tag moduleA2 "module-name-over" "1.1.1") => "module-name-over-1.1.1"
       (tag moduleA2 "sysA-container2" "1.1.1") => "sysA-container2-1.1.1")
      (fs/delete-dir monorepo))))

(deftest monorepo-artefact-name-with-semver
  (testing "testing a monorepo with two systems of two containers"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)]
      (facts
       (monorepo? monorepo) => truthy
       (monorepo? moduleA1) => truthy
       (monorepo? moduleA2) => truthy
       (monorepo? moduleB1) => truthy
       (monorepo? moduleB2) => truthy
       (artefact-name moduleA1 "sysA-container1" :scheme "semver") =in=> #"sysA-container1-1.3.4.*"
       (artefact-name moduleA2 "sysA-container2" :scheme "semver") =in=> #"sysA-container2-1.1.2.*"
       (artefact-name moduleB1 "sysB-container1" :scheme "semver") =in=> #"sysB-container1-1.2.0.*"
       (artefact-name moduleB2 "sysB-container2" :scheme "semver") =in=> #"sysB-container2-1.5.7.*"
       )
      (fs/delete-dir monorepo)))
  (testing "testing a monorepo with two systems of two containers"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)]

      (fs/delete-dir monorepo)))
  (testing "testing a monorepo with two systems of two containers"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)]

      (fs/delete-dir monorepo))))
