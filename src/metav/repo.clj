(ns metav.repo
  (:require [metav.git :as git]
            [me.raynes.fs :as fs]))

(def module-build-file "deps.edn")

(defn monorepo?
  "Does a single repo contains several modules? (dir with a build config like deps.edn in it). A monorepo is detected when the metav library is invoked correctly in a subdirectory of a git repo
  (so a deps.edn file is present in a subdirectory) "
  ([] (monorepo? nil))
  ([dir]
   (boolean (or (and (= (fs/normalized (fs/file dir))
                        (fs/normalized(fs/file (git/toplevel dir))))
                     (not-empty (fs/find-files dir #"deps.edn")))
                (and (not (nil? (git/prefix dir)))
                     (fs/file? (if dir
                                 (str dir "/" module-build-file)
                                 module-build-file)))))))

(defn dedicated-repo?
  "Does the current working directory contains a build system for a module (like deps.edn)"
  ([] (dedicated-repo? nil))
  ([repo-dir]
   ;;assume the working dir contains a deps.edn
   (and (nil? (git/prefix repo-dir))
        (fs/file? (if repo-dir
                    (str repo-dir "/" module-build-file)
                    module-build-file)))))
