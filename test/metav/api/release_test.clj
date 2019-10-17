(ns metav.api.release-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [me.raynes.fs :as fs]

    [metav.utils-test :as ut]
    [metav.git-shell :as gs]
    [metav.utils-test :as ut]
    [metav.git :as m-git]
    [metav.api.release :as m-release]

    [clojure.pprint :as pp]))


(deftest release-repo
  (testing "bump from a clean tagged repo, testing the spitted files"
    (ut/with-example-monorepo m
      (let [{:keys [remote monorepo modules] :as mono} m
            {moduleA1 :A1} modules

            options {:metav.release/without-sign true
                     :metav.release/spit true
                     :metav.spit/output-dir "resources"
                     :metav.spit/namespace "metav.meta"
                     :metav.spit/formats #{:edn :clj :json}}
            ctxt-A1 (ut/make-context moduleA1 options)
            ctxt-after-release (m-release/perform! ctxt-A1)

            {bumped-version :metav/version
             bumped-tag :metav/tag
             prefix :metav/version-prefix} ctxt-after-release

            [scm-base distance sha dirty?] (m-git/working-copy-description moduleA1 :prefix prefix)
            tag-verify (m-git/tag-verify monorepo bumped-tag)]
        (facts
          (str bumped-version) => "1.3.5"
          scm-base => "1.3.5"
          bumped-tag =>   "sysA-container1-1.3.5"
          (:exit tag-verify) => 1
          (fs/exists? (str moduleA1 "/resources/metav/meta.edn")) => true
          (fs/exists? (str moduleA1 "/resources/metav/meta.clj")) => true
          (fs/exists? (str moduleA1 "/resources/metav/meta.json")) => true)))))

;; TODO: port the other tests