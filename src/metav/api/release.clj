(ns metav.api.release
  (:require
    [clojure.spec.alpha :as s]))

;;----------------------------------------------------------------------------------------------------------------------
;; Release conf
;;----------------------------------------------------------------------------------------------------------------------
(def defaults-options
  #:metav.release{:level :patch
                  :without-sign false
                  :spit false
                  :without-push false})

(s/def :metav.release/level #{:major :minor :patch :alpha :beta :rc})
(s/def :metav.release/without-sign boolean?)
(s/def :metav.release/spit boolean?)
(s/def :metav.release/without-push boolean?)

(s/def :metav.release/options
  (s/keys :opt [:metav.release/level
                :metav.release/without-sign
                :metav.release/spit
                :metav.release/without-push]))







(comment
  (defn perform! [context]
    (perform*! (merge default-options context))))