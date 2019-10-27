(ns metav.domain.git-test
  (:require
   [clojure.test :as t :refer [deftest testing]]
   [testit.core :refer :all]
   [metav.domain.git :as git]
   [metav.git-shell :refer :all]))


(defn git-short-status [repo]
  (git/git-in-dir repo "status" "--short"))


(deftest check-assert-committed
  (testing "check that the git status command is correctly parsed"
    (let [repo (shell! (init!) (write-dummy-file-in!) (add!) (commit!))]
      (testing "Repo have all files committed"
        (fact (git-short-status repo) => [""])
        (fact (git/assert-committed? repo) => falsey))
      (shell-in-dir! repo (write-dummy-file-in!))
      (fact (first (git-short-status repo)) =in=> #"\\?\\? .*")
      (fact (git/assert-committed? repo) =throws=> Exception)
      (shell-in-dir! repo (add!))
      (fact (first (git-short-status repo)) =in=> #"A  .*")
      (fact (git/assert-committed? repo) =throws=> Exception)
      (shell-in-dir! repo (commit!))
      (fact (git/assert-committed? repo) => falsey))))


(comment
  (deftest prefix-and-toplevel
    (testing "toplevel is correct"
      (let [repo (shell! (init!) (commit!) (tag! "v1.2.3"))]))))
