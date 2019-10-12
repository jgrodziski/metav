(ns metav.version-test
  (:require
    [clojure.test :refer [deftest testing]]
    [testit.core :refer :all]
    [metav.version.protocols :as m-p]
    [metav.version.common :as m-v-common]
    [metav.maven :as m-maven]
    [metav.semver :as m-semver]))


(defn unsafe-bump [v & bumps]
  (reduce
    (fn [v b]
      (m-p/bump v b))
    v
    bumps))


(def safer-bump m-v-common/bump)


(defn semver-like-progression [v]
  (let [v* (atom v)
        bump! (fn [& bumps]
                (swap! v* #(apply unsafe-bump % bumps)))]
    (bump! :patch :patch :patch)
    (fact (str @v*) => "0.1.3")

    (bump! :patch :patch :patch)
    (fact (str @v*) => "0.1.6")

    (bump! :major :minor :patch)
    (fact (str @v*) => "1.1.1")

    (bump! :patch :minor)
    (fact (str @v*) => "1.2.0")

    (bump! :patch :major)
    (fact (str @v*) => "2.0.0")))


(deftest semver-like-bumps
  (semver-like-progression (m-semver/version))
  (semver-like-progression (m-maven/version)))


(deftest maven-like-bumps
  (let [v* (atom (m-maven/version))
        bump! (fn [& bumps]
                (swap! v* #(apply unsafe-bump % bumps)))]

    (bump! :patch :patch :patch :alpha)
    (fact (str @v*) => "0.1.3-alpha")

    (bump! :alpha)
    (fact (str @v*) => "0.1.3-alpha2")

    (bump! :beta :beta :beta)
    (fact (str @v*) => "0.1.3-beta3")

    (bump! :patch)
    (fact (str @v*) => "0.1.4")

    (bump! :rc)
    (fact (str @v*) => "0.1.4-rc")

    (bump! :minor)
    (fact (str @v*) => "0.2.0")

    (bump! :rc)
    (fact (str @v*) => "0.2.0-rc")

    (bump! :major)
    (fact (str @v*) => "1.0.0")))


(defn test-safer-version-progression [make-version]
  (testing "Every case here would duplicate versions"
    (let [v (make-version "0.0.0" 0 "1234" false)]
      (facts
        (str (unsafe-bump v :patch))       => "0.0.1"
        (str (safer-bump v :patch)) =throws=> Exception

        (str (unsafe-bump v :minor))       => "0.1.0"
        (str (safer-bump v :minor)) =throws=> Exception

        (str (unsafe-bump v :major))       => "1.0.0"
        (str (safer-bump v :major)) =throws=> Exception)))

  (testing "Only patch would duplicate versions"
    (let [v (make-version "0.0.1" 0 "1234" false)]
      (facts
        (str (unsafe-bump v :patch))       => "0.0.2"
        (str (safer-bump v :patch)) =throws=> Exception

        (str (unsafe-bump v :minor)) => "0.1.0"
        (str (safer-bump v :minor))  => "0.1.0"

        (str (unsafe-bump v :major)) => "1.0.0"
        (str (safer-bump v :major))  => "1.0.0")))

  (testing "Would duplicate version up to minor"
    (let [v (make-version "0.1.0" 0 "1234" false)]
      (facts
        (str (unsafe-bump v :patch))       => "0.1.1"
        (str (safer-bump v :patch)) =throws=> Exception

        (str (unsafe-bump v :minor))       => "0.2.0"
        (str (safer-bump v :minor)) =throws=> Exception

        (str (unsafe-bump v :major)) => "1.0.0"
        (str (safer-bump v :major))  => "1.0.0"))))


(deftest test-safer-bumps-semver-like
  (test-safer-version-progression m-semver/version)
  (test-safer-version-progression m-maven/version))


(deftest test-ordering-in-maven-specifics
  (let [v (m-maven/version "0.1.0-beta" 1 "1234" false)]
    (facts
      (str (unsafe-bump v :alpha)) => "0.1.0-alpha"
      (str (safer-bump v :alpha)) =throws=> Exception)))
