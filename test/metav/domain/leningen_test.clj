(ns metav.domain.leningen-test
  (:require [clojure.test :refer [deftest testing]]
            [clojure.java.shell :as shell]
            [testit.core :refer [fact =>]]
            [metav.domain.leningen :refer [sync-lein-version!]]
            [metav.git-shell :refer [write-dummy-project-clj-in!]]
            [me.raynes.fs :as fs]))

(deftest sync-lein-version!-test
  (binding [shell/*sh-dir* "target"]
    (try
      (write-dummy-project-clj-in! "lein-project")
      (testing "Syncing lein project version"
        (let [bumped-lein-project (sync-lein-version!
                                    {:metav/version     #metav.domain.version.semver.SemVer{:subversions [0 1 1], :distance 0, :sha "6d45", :dirty? false},
                                     :metav/working-dir "target/lein-project"})]
          (fact "returns the group id" (:metav.lein/group-id bumped-lein-project) => "my-group-id")
          (fact "returns the artifact name" (:metav.lein/artifact-name bumped-lein-project) => "my-artifact-id")
          (fact "returns the previous version" (:metav.lein/previous-version bumped-lein-project) =>  "0.1.0")
          (fact "returns the new version" (:metav.lein/version bumped-lein-project) => "0.1.1")
          (fact "replaces version in project.clj file"
                (slurp "target/lein-project/project.clj")
                =>
                "(defproject my-group-id/my-artifact-id \"0.1.1\"\n  :dependencies [[awesome/lib \"0.0.1\"]])\n")))
      (finally (fs/delete-dir "lein-project")))))
