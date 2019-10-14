(ns metav.api-test
  (:require
    [clojure.test :as test :refer [deftest testing]]
    [testit.core :refer :all]
    [clojure.java.shell :as shell]
    [metav.git :as m-git]
    [metav.git-shell :as gs]
    [metav.version.protocols :as m-p]
    [me.raynes.fs :as fs]
    [metav.api.context :as m-ctxt]))


