(ns metav.release-test
  (:require [testit.core :refer :all]
            [metav.release :as release :refer [execute!]]
            [metav.git-shell :refer :all]
            [metav.display :refer [version module-name prefix artefact-name *separator*]]
            [metav.git :as git]
            [metav.display-test :refer [monorepo]]
            [clojure.test :as t :refer :all]
            [me.raynes.fs :as fs]))

(deftest release-repo
  (testing "bump from a clean tagged repo"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo);module A1 latest tag is 1.3.4
          [module-name bumped-version tag push-result] (release/execute! moduleA1 "semver" :patch)
          [scm-base distance sha dirty?] (git/working-copy-description moduleA1 :prefix  (prefix moduleA1))]
      (facts
         (str bumped-version) => "1.3.5"
         scm-base => "1.3.5"
         tag =>   "sysA-container1-1.3.5")
      (fs/delete monorepo)
      (fs/delete remote)))
  (testing "bump from a clean tagged repo, two patch release, one minor, then one major"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo);module A2 latest tag is 1.1.2
          [_ bumped-version1 tag1 _] (release/execute! moduleA2 "semver" :patch)
          [scm-base1 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2))
          metadata1 (git/tag-message monorepo tag1)
          _ (Thread/sleep 500);need to wait because the time resolution of the git describe command needs some time to elapse before asking whether a new tag is available

          [_ bumped-version2 tag2 _] (release/execute! moduleA2 "semver" :patch)
          [scm-base2 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2))
          metadata2 (git/tag-message monorepo tag2)
          _ (Thread/sleep 500)

          [_ bumped-version3 tag3 _] (release/execute! moduleA2 "semver" :minor)
          [scm-base3 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2))
          metadata3 (git/tag-message monorepo tag3)
          _ (Thread/sleep 500)

          [_ bumped-version4 tag4 _] (release/execute! moduleA2 "semver" :major)
          [scm-base4 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2))
          metadata4 (git/tag-message monorepo tag4)
          _ (Thread/sleep 500)
          ]
      (facts
       (str bumped-version1) => "1.1.3"
       scm-base1 => "1.1.3"
       tag1 => "sysA-container2-1.1.3"
       metadata1 => truthy

       (str bumped-version2) => "1.1.4"
       scm-base2 => "1.1.4"
       tag2 => "sysA-container2-1.1.4"
       metadata2 => truthy

       (str bumped-version3) => "1.2.0"
       scm-base3 => "1.2.0"
       tag3 => "sysA-container2-1.2.0"
       metadata3 => truthy

       (str bumped-version4) => "2.0.0"
       scm-base4 => "2.0.0"
       tag4 => "sysA-container2-2.0.0"
       metadata4 => truthy
       )
      (fs/delete monorepo)
      (fs/delete remote))))

;; execute several release in different module with different level each time (major, minor, patch)
;; assert the bump function works correctly (bump the appropriate level)
