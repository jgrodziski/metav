(ns metav.release-test
  (:require [testit.core :refer :all]
            [metav.release :as release :refer [execute]]
            [metav.git-shell :refer :all]
            [metav.display :refer [version module-name artefact-name]]
            [metav.git :as git]
            [metav.display-test :refer [monorepo]]
            [clojure.test :as t :refer :all]
            [me.raynes.fs :as fs]))

(deftest release-repo
  (testing "bump from a clean tagged repo"
    (let [[monorepo moduleA1 moduleA2 moduleB1 moduleB2] (monorepo);module A1 latest tag is 1.3.4
          bumped-version (release/execute moduleA1 "semver" :patch)
          [base distance sha dirty?] (git/working-copy-description moduleA1)]
      (prn monorepo)
      (prn bumped-version )
      (facts
       (str bumped-version) => "1.3.5"
       base => "1.3.5")
      )))

;; execute several release in different module with different level each time (major, minor, patch)
;; assert the bump function works correctly (bump the appropriate level)
