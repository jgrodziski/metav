(ns metav.repo
  (:require [metav.git :as git]
            [me.raynes.fs :as fs]))

(def module-build-file "./deps.edn")

(defn monorepo?
  "Does a single repo contains several modules? (dir with a build config like deps.edn in it). A monorepo is detected when the metav library is invoked correctly in a subdirectory of a git repo
  (so a deps.edn file is present in a subdirectory) "
  []
  (and (not (nil? (git/prefix))) (fs/file? module-build-file)))

(defn dedicated-repo?
  "Does the current working directory contains a build system for a module (like deps.edn)"
  []
  ;;assume the working dir contains a deps.edn
  (and (nil? (git/prefix)) (fs/file? module-build-file)))
