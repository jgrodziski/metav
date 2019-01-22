(ns metav.release-test
  (:require [testit.core :refer :all]
            [metav.release :as release]
            [metav.git-shell :refer :all]
            [metav.display :refer :all]
            [metav.git :as git]
            [clojure.test :as t :refer :all]
            [me.raynes.fs :as fs]))

(deftest release-repo
  (testing "from a clean tagged repo"
    (let [repo ()])))
