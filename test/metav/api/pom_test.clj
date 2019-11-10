(ns metav.api.pom-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [clojure.data.xml :as xml]
    [testit.core :refer :all]
    [me.raynes.fs :as fs]


    [metav.api :as api]
    [metav.domain.pom :as pom]
    [metav.test-utils :as test-utils]))


(deftest bad-context
  (testing "Metav won't sync-pom! on bad context"
    (fact (api/sync-pom! {}) =throws=> Exception)))


(def pom-tags #{:groupId
                :artifactId
                :version
                :name})

(defn extract-pom-data [pom]
  (-> pom
      (-> :content
          (->> (into {} (map #(vector (-> % :tag name keyword)
                                      (-> % :content first)))))
          (-> (select-keys pom-tags)))))


(defn test-pom [ctxt]
  (let [pom-data (-> ctxt
                     (-> :metav.maven.pom/sync-path
                         pom/read-xml
                         extract-pom-data))]
    (facts
      (:groupId pom-data)    => (:metav.maven/group-id ctxt)
      (:name pom-data)       => (:metav.maven.pom/name ctxt)
      (:artifactId pom-data) => (:metav/artefact-name ctxt)
      (:version pom-data)    => (str (:metav/version ctxt)))))


(deftest pom-sync!
  (test-utils/with-example-monorepo m
    (let [{:keys [monorepo modules]} m
          {moduleA1 :A1} modules

          ctxt-A1 (-> moduleA1
                      test-utils/make-context
                      api/sync-pom!)]
      (test-pom ctxt-A1))))
