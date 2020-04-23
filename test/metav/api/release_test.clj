(ns metav.api.release-test
  (:require
    [clojure.test :as test :refer [deftest testing is]]
    [testit.core :refer :all]
    [me.raynes.fs :as fs]

    [metav.api :as api]
    [metav.domain.git :as git]
    [metav.git-shell :as gs]
    [metav.api.pom-test :as pom-test]
    [metav.test-utils :as test-utils]))



(deftest bad-context
  (testing "Metav won't release on bad context"
    (fact (api/release! {}) =throws=> Exception)))

(test-utils/with-example-monorepo m
      (let [{:keys [monorepo modules]}             m
            {moduleA1 :A1}                         modules
            options                                {:metav.git/without-sign true
                                                    :metav.release/spit     true
                                                    :metav.spit/pom         true
                                                    :metav.spit/output-dir  "resources"
                                                    :metav.spit/namespace   "metav.meta"
                                                    :metav.spit/formats     #{:edn :clj :json}}
            ctxt-A1                                (test-utils/make-context moduleA1 options)]

        ctxt-A1))


;; TODO: can't sign tags in tests for now -> need to setup the environment so that gpg can be found by the java procees spawned with Runtime.exec
;; TODO: Figure out how to use the git env from metav.git-shell/GIT_ENV when calling metav.domain.git functions.
;;       Right now, it's my git conf that's used instead.
(deftest release-repo
  (testing "bump from a clean tagged repo, testing the spitted files"
    (test-utils/with-example-monorepo m
      (let [{:keys [monorepo modules]}             m
            {moduleA1 :A1}                         modules
            options                                {:metav.git/without-sign true
                                                    :metav.release/spit     true
                                                    :metav.spit/pom         true
                                                    :metav.spit/output-dir  "resources"
                                                    :metav.spit/namespace   "metav.meta"
                                                    :metav.spit/formats     #{:edn :clj :json}}
            ctxt-A1                                (test-utils/make-context moduleA1 options)
            ctxt-after-release                     (api/release! ctxt-A1)
            {bumped-version :metav/version
             bumped-tag     :metav/tag
             prefix         :metav/version-prefix} ctxt-after-release
            [scm-base]                             (git/working-copy-description moduleA1 :prefix prefix)]

        ;(clojure.pprint/pprint "pom" ctxt-after-release)
        (pom-test/test-pom ctxt-after-release)
        (is (thrown? clojure.lang.ExceptionInfo (git/tag-verify monorepo bumped-tag)))
 
        ;;(ex-info? (str "Can't verify tag " bumped-tag " with GPG signature in directory " moduleA1)
        ;;{:working-dir moduleA1 :tag bumped-tag :result nil})
        (facts
          (str bumped-version)                                     => "1.3.5"
          scm-base                                                 => "1.3.5"
          bumped-tag                                               => "sysA-container1-1.3.5"
          (fs/exists? (str moduleA1 "/resources/metav/meta.edn"))  => true
          (fs/exists? (str moduleA1 "/resources/metav/meta.clj"))  => true
          (fs/exists? (str moduleA1 "/resources/metav/meta.json")) => true))))

  (testing "bump from a clean tagged repo, two patch releases, then one minor, then one major"
    (test-utils/with-example-monorepo m
      (let [{:keys [remote monorepo modules] :as mono} m
            {moduleA2 :A2}                             modules

            general-option {:metav.spit/formats       #{:edn}
                            :metav.git/without-sign   false
                            :metav.release/level      :patch
                            :metav.release/spit       true
                            :metav.spit/pom           true
                            :metav.release/output-dir "resources"
                            :metav.release/namespace  "meta"}

            release! (fn [level]
                       (let [options            (assoc general-option :metav.release/level level)
                             ctxt-after-release (api/release! (test-utils/make-context moduleA2 options))

                             {bumped-version :metav/version
                              bumped-tag     :metav/tag
                              prefix         :metav/version-prefix} ctxt-after-release

                             [scm-base] (git/working-copy-description moduleA2 :prefix prefix)]
                         (Thread/sleep 500) ;need to wait because the time resolution of the git describe command needs some time to elapse before asking whether a new tag is available

                         (pom-test/test-pom ctxt-after-release)

                         {:bumped-version bumped-version
                          :bumped-tag     bumped-tag
                          :scm-base       scm-base
                          :tag-verify     (git/tag-verify monorepo bumped-tag)
                          :metadata       (git/tag-message monorepo bumped-tag)}))

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
            (str (:bumped-version release1))                  => "1.1.3"
            (:scm-base release1)                              => "1.1.3"
            (:bumped-tag release1)                            => "sysA-container2-1.1.3"
            (:tag-verify release1)                            => truthy
            (:metadata release1)                              => truthy
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

;(release-repo)
