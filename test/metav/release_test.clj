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
  (testing "from a clean tagged repo"
    (let [[monorepo moduleA1 moduleA2 moduleB1 moduleB2] (monorepo)
          bumped-version (release/execute moduleA1 "semver" :patch)]
      (prn monorepo)
      (prn bumped-version )
      )))

;; execute several release in different module with different level each time (major, minor, patch)
;; assert the bump function works correctly (bump the appropriate level)
