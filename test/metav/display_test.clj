(ns metav.display-test
  (:require [clojure.test :refer :all]
            [testit.core :refer :all]
            [metav.git-shell :refer :all]
            [metav.display :refer :all]
            [metav.git :as git]
            [me.raynes.fs :as fs]))

(defn setup-tear-down [test-to-run]
  (test-to-run))

(use-fixtures :once setup-tear-down)

(deftest bare-repo-with-semver
  (testing "testing initialized repo should return 0.1.0"
    (let [repo (shell! (init!))]
      (fact (str (version "semver" repo)) => "0.1.0")
      (fs/delete-dir repo)))
  (testing "testing untracked file should not impact the version"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111"))]
      (fact (str (version "semver" repo)) => "0.1.0")
      (fs/delete-dir repo)))
  (testing "testing added should be dirty"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!))]
      (fact (str (version "semver" repo)) => "0.1.0")
      (fs/delete-dir repo)))
  (testing "testing committed"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!))]
      (prn (str (version "semver" repo)))
      (fact (str (version "semver" repo)) =in=> #"0.1.1+.*")
      (fs/delete-dir repo)))
  (testing "testing tagged"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!) (tag! "v1.3.0"))]
      (fact (str (version "semver" repo)) => "1.3.0" )
      (fs/delete-dir repo)))
  (testing "testing a file add should give dirty"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!) (tag! "v1.3.0")
                       (write-dummy-file-in! "2" "22" "222") (add!))]
      (fact (str (version "semver" repo)) => "1.3.0+DIRTY")
      (fs/delete-dir repo)))
  (testing "testing 2 commits should increase the patch number by 2"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!) (tag! "v1.3.0")
                       (write-dummy-file-in! "2" "22" "222") (add!) (commit!)
                       (write-dummy-file-in! "3" "33" "333") (add!) (commit!))]
      (fact (str (version "semver" repo)) =in=> #"1.3.2+.*")
      (fs/delete-dir repo)))
  (testing "testing tagged"
    (let [repo (shell! (init!) (write-dummy-file-in! "1" "11" "111") (add!) (commit!) (tag! "v1.3.0"))]
      (fact (str (version "semver" repo)) => "1.3.0" )
      (fs/delete-dir repo))))
