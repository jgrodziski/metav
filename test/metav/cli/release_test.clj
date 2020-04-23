(ns metav.cli.release-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [metav.git-shell :as gs]
    [me.raynes.fs :as fs]
    [metav.test-utils :as test-utils]
    [metav.domain.git :as git]
    [metav.cli.release :as cli-release]
    [metav.api.pom-test :as pom-test]))


(deftest release-with-cli
  (testing "Major release with cli and without signature."
    (test-utils/with-example-monorepo m
      (let [{:keys [modules monorepo remote]} m
            {:keys [p2]}                      modules]
        (gs/shell-in-dir! p2
          (gs/write-dummy-file-in! p2 "src")
          (gs/add!)
          (gs/commit!))
        (fs/with-cwd p2
          (let [release-res                         (cli-release/main* "--full-name"
                                                                       "--spit"
                                                                       "--pom"
                                                                       "--without-sign"
                                                                       "-f" "edn"
                                                                       "-o" "resources"
                                                                       "-n" "meta"
                                                                       "major")
                {bumped-version :metav/version
                 bumped-tag     :metav/tag
                 prefix         :metav/version-prefix
                 :as            ctx-after-release} (:ret release-res)
                [scm-base]                         (git/working-copy-description p2 :prefix prefix)
                metadata                           (git/tag-message monorepo bumped-tag)
                remote-tags                        (git/list-remote-tags remote)
                tag                                (str (fs/base-name monorepo) "-project2-2.0.0")]
            (pom-test/test-pom ctx-after-release)
            (facts (str bumped-version)                        => "2.0.0"
                   scm-base                                    => "2.0.0"
                   bumped-tag                                  => tag
                   metadata                                    => truthy
                   (get remote-tags tag)                       => truthy
              (fs/exists? (fs/file p2 "resources" "meta.edn")) => true))))))
  (testing "Minor release with cli and with signature."
    (test-utils/with-example-monorepo m
      (let [{:keys [modules monorepo]} m
            {:keys [p2]}               modules]

        (gs/shell-in-dir! p2
          (gs/write-dummy-file-in! p2 "src")
          (gs/add!)
          (gs/commit!))

        (fs/with-cwd p2
          (let [release-res                         (cli-release/main* "--full-name"
                                                                       "--spit"
                                                                       "--pom"
                                                                       "-f" "edn"
                                                                       "-o" "resources"
                                                                       "-n" "meta"
                                                                       "minor")
                {bumped-version :metav/version
                 bumped-tag     :metav/tag
                 prefix         :metav/version-prefix
                 :as            ctxt-after-release} (:ret release-res)
                [scm-base]                          (git/working-copy-description p2 :prefix prefix)
                ;;as there is a GPG signature we can verify the tag
                tag-verify                          (git/tag-verify monorepo bumped-tag)
                metadata                            (git/tag-message monorepo bumped-tag)]
            (pom-test/test-pom ctxt-after-release)
            (facts
              (str bumped-version) => "1.2.0"
              scm-base => "1.2.0"
              bumped-tag => (str (fs/base-name monorepo) "-project2-1.2.0")
              tag-verify => truthy
              metadata => truthy
              (fs/exists? (fs/file p2 "resources" "meta.edn")) => true)))))))

