(ns metav.api.release-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [me.raynes.fs :as fs]

    [metav.utils-test :as ut]
    [metav.git-shell :as gs]
    [metav.utils-test :as ut]
    [metav.domain.git :as git]
    [metav.api :as api]))


;; TODO: can't sign tags in tests for now -> need to setup the environment so that gpg can be found by the java procees spawned with Runtime.exec
;; TODO: Figure out how to use the git env from metav.git-shell/GIT_ENV when calling metav.domain.git functions.
;;       Right now, it's my git conf that's used instead.
(deftest release-repo
  (testing "bump from a clean tagged repo, testing the spitted files"
    (ut/with-example-monorepo m
      (let [{:keys [monorepo modules]} m
            {moduleA1 :A1} modules

            options {:metav.release/without-sign true
                     :metav.release/spit true
                     :metav.spit/output-dir "resources"
                     :metav.spit/namespace "metav.meta"
                     :metav.spit/formats #{:edn :clj :json}}
            ctxt-A1 (ut/make-context moduleA1 options)
            ctxt-after-release (api/release! ctxt-A1)

            {bumped-version :metav/version
             bumped-tag :metav/tag
             prefix :metav/version-prefix} ctxt-after-release

            [scm-base] (git/working-copy-description moduleA1 :prefix prefix)
            tag-verify (git/tag-verify monorepo bumped-tag)]
        (facts
          (str bumped-version) => "1.3.5"
          scm-base => "1.3.5"
          bumped-tag =>   "sysA-container1-1.3.5"
          (:exit tag-verify) => 1
          (fs/exists? (str moduleA1 "/resources/metav/meta.edn")) => true
          (fs/exists? (str moduleA1 "/resources/metav/meta.clj")) => true
          (fs/exists? (str moduleA1 "/resources/metav/meta.json")) => true))))

  (testing "bump from a clean tagged repo, two patch releases, then one minor, then one major"
    (ut/with-example-monorepo m
      (let [{:keys [remote monorepo modules] :as mono} m
            {moduleA2 :A2} modules

            general-option {:metav.spit/formats         #{:edn}
                            :metav.release/level        :patch
                            :metav.release/spit         true
                            :metav.release/without-sign true
                            :metav.release/output-dir   "resources"
                            :metav.release/namespace    "meta"}

            release! (fn [level]
                       (let [options (assoc general-option :metav.release/level level)
                             ctxt-after-release (api/release! (ut/make-context moduleA2 options))

                             {bumped-version :metav/version
                              bumped-tag     :metav/tag
                              prefix         :metav/version-prefix} ctxt-after-release

                             [scm-base] (git/working-copy-description moduleA2 :prefix prefix)]
                         (Thread/sleep 500) ;need to wait because the time resolution of the git describe command needs some time to elapse before asking whether a new tag is available
                         {:bumped-version bumped-version
                          :bumped-tag bumped-tag
                          :scm-base scm-base
                          :tag-verify (git/tag-verify monorepo bumped-tag)
                          :metadata (git/tag-message monorepo bumped-tag)}))

            release1 (release! :patch)]

        (fact
          (release! :patch) =throws=> Exception) ;; no change :patch would duplicate

        (gs/shell-in-dir! moduleA2
          (gs/write-dummy-file-in! "src")
          (gs/add!)
          (gs/commit!))
        (let [release2 (release! :patch)
              release3 (release! :minor)
              release4 (release! :major)]
          (facts
            (str (:bumped-version release1)) => "1.1.3"
            (:scm-base release1)  => "1.1.3"
            (:bumped-tag release1) => "sysA-container2-1.1.3"
            (:tag-verify release1) => truthy
            (:metadata release1)   => truthy
            (fs/exists? (str moduleA2 "/resources/meta.edn")) => true

            (str (:bumped-version release2)) => "1.1.4"
            (:scm-base release2)  => "1.1.4"
            (:bumped-tag release2) => "sysA-container2-1.1.4"
            (:metadata release2)   => truthy

            (str (:bumped-version release3)) => "1.2.0"
            (:scm-base release3)  => "1.2.0"
            (:bumped-tag release3) => "sysA-container2-1.2.0"
            (:metadata release3)   => truthy

            (str (:bumped-version release4)) => "2.0.0"
            (:scm-base release4)  => "2.0.0"
            (:bumped-tag release4) => "sysA-container2-2.0.0"
            (:metadata release4)   => truthy))))))
