(ns metav.release-test
  (:require [testit.core :refer :all]
            [metav.release :as release :refer [execute!]]
            [metav.git-shell :refer :all]
            [metav.metadata :refer [invocation-context version module-name prefix artefact-name *separator*]]
            [metav.git :as git]
            [metav.metadata-test :refer [monorepo]]
            [clojure.test :as t :refer :all]
            [me.raynes.fs :as fs]))

(deftest release-repo
  (testing "bump from a clean tagged repo, testing the spitted files"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo);module A1 latest tag is 1.3.4, moduleB3 should be 0.1.0 not tagged
          options {:without-sign true :spit true :output-dir "resources" :namespace "metav.meta" :formats "edn,clj,json"}
          [module-name bumped-version tag push-result] (release/execute! (invocation-context options moduleA1) :patch)
          [scm-base distance sha dirty?] (git/working-copy-description moduleA1 :prefix (prefix moduleA1 module-name))
          tag-verify (git/tag-verify monorepo tag)]
      (facts
         (str bumped-version) => "1.3.5"
         scm-base => "1.3.5"
         tag =>   "sysA-container1-1.3.5"
         (:exit tag-verify) => 1
         (fs/exists? (str moduleA1 "/resources/metav/meta.edn")) => true
         (fs/exists? (str moduleA1 "/resources/metav/meta.clj")) => true
         (fs/exists? (str moduleA1 "/resources/metav/meta.json")) => true)
      (fs/delete monorepo)
      (fs/delete remote)))
  
  (testing "bump from a clean tagged repo, two patch release, one minor, then one major"
    (let [[monorepo remote moduleA1 moduleA2 moduleB1 moduleB2] (monorepo);module A2 latest tag is 1.1.2
          options {:spit true :output-dir "resources" :namespace "meta" :formats "edn"}
          [module-name1 bumped-version1 tag1 _] (release/execute! (invocation-context options moduleA2) :patch )
          [scm-base1 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2 module-name1))
          tag-verify (git/tag-verify monorepo tag1)
          metadata1 (git/tag-message monorepo tag1)
          _ (Thread/sleep 500);need to wait because the time resolution of the git describe command needs some time to elapse before asking whether a new tag is available

          [module-name2 bumped-version2 tag2 _] (release/execute! (invocation-context options moduleA2) :patch)
          [scm-base2 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2 module-name2))
          metadata2 (git/tag-message monorepo tag2)
          _ (Thread/sleep 500)

          [module-name3 bumped-version3 tag3 _] (release/execute! (invocation-context options moduleA2) :minor)
          [scm-base3 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2 module-name3))
          metadata3 (git/tag-message monorepo tag3)
          _ (Thread/sleep 500)

          [module-name4 bumped-version4 tag4 _] (release/execute! (invocation-context options moduleA2) :major)
          [scm-base4 _ _ _] (git/working-copy-description moduleA2 :prefix (prefix moduleA2 module-name4))
          metadata4 (git/tag-message monorepo tag4)
          _ (Thread/sleep 500)
          ]
      (facts
       (str bumped-version1) => "1.1.3"
       scm-base1 => "1.1.3"
       tag1 => "sysA-container2-1.1.3"
       tag-verify => truthy
       metadata1 => truthy
       (fs/exists? (str moduleA2 "/resources/meta.edn")) => true

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

(deftest release-test-two-times-with-same-level
  (testing "release a repo two times with the same release level (minor)"
    (let [[monorepo remote _ _ _ _ moduleB3] (monorepo)
          options {:spit true :output-dir "resources" :namespace "meta" :formats "edn"}
          [module-name bumped-version1 tag1 _] (release/execute! (invocation-context options moduleB3) :minor )
          _ (write-dummy-file-in! "sysB" "container3" "src" "dummy1")
          _ (add!)
          _ (commit!)
          _ (Thread/sleep 500)
          [module-name bumped-version2 tag2 _] (release/execute! (invocation-context options moduleB3) :minor )
          ]
      (println monorepo)
      (facts
       (str bumped-version1) => "0.2.0"
       (str bumped-version2) => "0.3.0"
       )
      (fs/delete monorepo)
      (fs/delete remote)
      )))
;; execute several release in different module with different level each time (major, minor, patch)
;; assert the bump function works correctly (bump the appropriate level)
