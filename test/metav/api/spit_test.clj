(ns metav.api.spit-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [clojure.string :as string]
    [clojure.edn :as edn]
    [me.raynes.fs :as fs]

    [metav.git-shell :as gs]
    [metav.utils-test :as ut]

    [metav.api.spit :as m-spit]
    [metav.utils :as u]))


(defn parse-rendered [text]
  (->> text
       string/split-lines
       (map #(re-find #": (.*$)" %))
       (map second)
       (zipmap [:module-name :version :tag :generated-at :path])))


(defn test-spits [repo]
  (let [context (ut/make-context repo {:metav.spit/namespace "metav.vfile"
                                       :metav.spit/template "mustache-template.txt"
                                       :metav.spit/rendering-output "resources/rendered.txt"
                                       :metav.spit/formats #{:edn}})
        [edn-file rendered-file] (m-spit/perform! context)]

    (testing "Files have been created."
      (fs/with-cwd repo
        (facts
          (fs/exists? edn-file)      => truthy
          (fs/exists? rendered-file) => truthy

          (u/inside-cwd? edn-file)       => truthy
          (u/inside-cwd? rendered-file)  => truthy)))

    (testing "files content are equivalent"
      (let [edn-produced (-> edn-file slurp edn/read-string)
            parsed-rendered (-> rendered-file slurp parse-rendered)]
        (fact
          edn-produced => parsed-rendered)))))


(deftest spiting-test
  (testing "In dedicated repo."
    (ut/with-repo repo
      (ut/prepare-base-repo! repo)
      (test-spits repo)))

  (testing "In monorepo"
    (ut/with-example-monorepo m
      (let [{:keys [remote monorepo modules] :as mono} m
            {project1 :p1
             project2 :p2
             moduleA1 :A1
             moduleA2 :A2
             moduleB1 :B1
             moduleB2 :B2
             moduleB3 :B3} modules]
        (fs/mkdir (fs/file project1 "resources"))
        (test-spits project1)

        (fs/mkdir (fs/file moduleB2 "resources"))
        (test-spits moduleB2)

        (test-spits moduleB3) ; actually no need to create resources dir, spit! creates it.

        (gs/shell-in-dir! monorepo
          (gs/sh-p "ls project1"))))))

(deftest corner-cases
  (testing "With en empty formats set and no render config, nothing is rendered."
    (ut/with-repo repo
      (ut/prepare-base-repo! repo)
      (let [ctxt (ut/make-context repo {:metav.spit/formats #{}})
            spitted (m-spit/perform! ctxt)]
        (fact
          (empty? spitted) => truthy)))))
