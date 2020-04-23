(ns metav.api.spit-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [clojure.string :as string]
    [clojure.edn :as edn]
    [me.raynes.fs :as fs]

    [metav.api :as api]
    [metav.utils :as utils]
    [metav.test-utils :as test-utils]))


(deftest bad-context
  (testing "Metav won't release on bad context"
    (fact (api/spit! {}) =throws=> Exception)))


(defn parse-rendered [text]
  (->> text
       string/split-lines
       (map #(re-find #": (.*$)" %))
       (map second)
       (zipmap [:module-name :version :tag :generated-at :path])))


(defn test-spits [repo]
  (let [context (test-utils/make-context repo {:metav.spit/namespace        "metav.vfile"
                                               :metav.spit/template         "mustache-template.txt"
                                               :metav.spit/rendering-output "resources/rendered.txt"
                                               :metav.spit/formats          #{:edn}})
        new-ctxt (api/spit! context)
        {:keys [data template] :as spitted-result} (:metav.spit/spitted new-ctxt)]
    (testing "Files have been created."
      (clojure.pprint/pprint spitted-result)
      (facts
        (fs/exists? (:edn data))      => truthy
        (fs/exists? (:rendered-file template)) => truthy

        (utils/ancestor? repo (:edn data))       => truthy
        (utils/ancestor? repo (:rendered-file template))  => truthy))

    (testing "files content are equivalent"
      (let [edn-produced (-> (:edn data) slurp edn/read-string)
            parsed-rendered (-> template :rendered-file slurp parse-rendered)]
        (fact
          edn-produced => parsed-rendered)))))


(deftest spiting-test
  (testing "In dedicated repo."
    (test-utils/with-repo repo
      (test-utils/prepare-base-repo! repo)
      (test-spits repo)))

  (testing "In monorepo"
    (test-utils/with-example-monorepo m
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

        (test-spits moduleB3))))

  (testing "Spitting with a template parameters and 3 formats should render the template and also spitting the data files"
    (test-utils/with-repo repo
      (test-utils/prepare-base-repo! repo)
      (let [context (test-utils/make-context repo {:metav.spit/namespace        "metav.vfile"
                                                   :metav.spit/template         "mustache-template.txt"
                                                   :metav.spit/pom              true
                                                   :metav.spit/rendering-output "resources/rendered.txt"
                                                   :metav.spit/formats          #{:edn :json :cljs :clj} })
            {:keys [pom-file-path data template] :as spitted-result} (:metav.spit/spitted (api/spit! context))]
        (facts
         (fs/exists? pom-file-path)                       => truthy
         (fs/exists? (:edn  data))                        => truthy
         (fs/exists? (:json data))                        => truthy
         (fs/exists? (:cljs data))                        => truthy
         (fs/exists? (:clj  data))                        => truthy
         (fs/exists? (:rendered-file template))           => truthy
         (utils/ancestor? repo (:rendered-file template)) => truthy))))

  (testing "Spitting with only a template parameters should render the template and not spitting the data files unless explicitly given in the inputs"
    (test-utils/with-repo repo
      (test-utils/prepare-base-repo! repo)
      (let [context (test-utils/make-context repo {:metav.spit/namespace        "metav.vfile"
                                                   :metav.spit/template         "mustache-template.txt"
                                                   :metav.spit/rendering-output "resources/rendered.txt"})
            {:keys [pom-file-path data template] :as spitted-result} (:metav.spit/spitted (api/spit! context))]
        (facts
         (fs/exists? pom-file-path)                       => falsey
         (fs/exists? (:edn data))                         => falsey
         (fs/exists? (:rendered-file template))           => truthy
         (utils/ancestor? repo (:rendered-file template)) => truthy)))))


(deftest corner-cases
  (testing "With en empty formats set and no render config, nothing is rendered."
    (test-utils/with-repo repo
      (test-utils/prepare-base-repo! repo)
      (let [ctxt (test-utils/make-context repo {:metav.spit/formats #{}})
            new-ctxt (api/spit! ctxt)
            spitted (:metav.spit/spitted new-ctxt)]
        (fact
          (empty? spitted) => truthy)))))
