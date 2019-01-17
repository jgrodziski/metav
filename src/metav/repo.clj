(ns metav.repo
  (:require [metav.git :as git]
            [me.raynes.fs :as fs]))

(def module-build-file "./deps.edn")

(defn monorepo?
  "A monorepo is detected when the metav library is invoked correctly in a subdirectory of a git repo
  (so a deps.edn file is present in a subdirectory) "
  []
  (and (not (nil? (git/prefix))) (fs/file? module-build-file)))

(defn dedicated-repo? []
  ;;assume the working dir contains a deps.edn
  (and (nil? (git/prefix)) (fs/file? module-build-file)))
