(ns metav.api.display-test
  (:require
    [clojure.string :as string]
    [clojure.test :refer :all]
    [testit.core :refer :all]
    [metav.test-utils :as test-utils]
    [metav.git-shell :as gs]
    [metav.api :as api]))


(deftest simple-display
  (test-utils/with-repo repo
    (test-utils/prepare-base-repo! repo)
    (let [context (api/make-context {:metav/working-dir repo})
          displayed (with-out-str (api/display context))
          [n v] (string/split displayed #"(\t|\n)")]
      (facts
        (:metav/artefact-name context) => n
        (-> context :metav/version str) => v))))