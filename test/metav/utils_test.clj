(ns metav.utils-test
  (:require
    [clojure.test :refer [deftest testing]]
    [testit.core :refer :all]
    [metav.utils :as utils]))


(deftest test-ensure-keys
  (let [side-effect-counter (atom 0)
        side-effect! #(swap! side-effect-counter inc)
        operation #(utils/ensure-keys %
                                      :a 1
                                      :b 2
                                      :c (do (side-effect!)
                                             (side-effect!)
                                             5))]
    (testing "Side effect should occur twice"
      (let [before @side-effect-counter
            res (operation {})
            after @side-effect-counter]
        (facts
          res
          => {:a 1
              :b 2
              :c 5}

          after => (+ 2 before))))

    (testing "Side effect shoudn't occur."
      (let [before @side-effect-counter
            res (operation {:c :not-changed})
            after @side-effect-counter]
        (facts
          res => {:a 1
                  :b 2
                  :c :not-changed}
          before => after)))

    (testing "When a key appears twice, the first occurrence is kept, testifying to the threaded behavior."
      (let [before @side-effect-counter
            res (utils/ensure-keys {} :a 1 :b 2 :a (do (side-effect!) 3))
            after @side-effect-counter]
        (facts
          before => after
          res => {:a 1 :b 2})))))
