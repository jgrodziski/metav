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

(def pom-tags #{:groupId :artifactId :version :name})

(defn extract-pom-data [pom]
  (-> pom
      (-> :content
          (->> (into {} (map #(vector (-> % :tag name keyword)
                                      (-> % :content first)))))
          (-> (select-keys pom-tags)))))


(defn test-pom [ctx]
  (let [{:keys [groupId name artifactId version] :as pom-data} (-> (:metav.maven.pom/pom-file-path ctx)
                                                                   pom/read-xml
                                                                   extract-pom-data)]
    (println "pom data" pom-data)
    (println "ctx" ctx)
    (facts groupId     => (:metav.maven/group-id ctx)
           name        => (:metav.maven.pom/name ctx)
           artifactId  => (:metav/artefact-name ctx)
           version     => (str (:metav/version ctx)))))


(deftest pom-sync!
  (test-utils/with-example-monorepo m
    (let [{:keys [monorepo modules]} m
          {moduleA1 :A1}             modules
          ctx-A1                    (-> moduleA1
                                        test-utils/make-context
                                        api/sync-pom!)]
      (test-pom ctx-A1))))
