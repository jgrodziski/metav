(ns metav.display-test
  (:require [clojure.test :refer :all]
            [metav.git-shell :refer :all]
            [metav.display :refer :all]))

(defn setup-tear-down [test-to-run]
  (test-to-run))

(use-fixtures :once setup-tear-down)


;; test0.repo: (shell! (init!) (commit!) (tag! "v1.2.3") (commit!))

(deftest bare-repo
  (testing "testing "
    (let [repo (shell! (init!) (commit!) (tag! "v1.2.3"))]
      (println (version repo)))))
