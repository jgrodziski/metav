(ns metav.git-test
  (:require [metav.git :as git]
            [metav.git-shell :refer :all]
            [clojure.test :as t :refer [deftest testing]]))

(comment 
  (deftest prefix-and-toplevel
    (testing "toplevel is correct"
      (let [repo (shell! (init!) (commit!) (tag! "v1.2.3"))]))))

