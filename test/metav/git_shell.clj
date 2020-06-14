(ns metav.git-shell
  "Utility functions for manipulating a test git repo"
  (:import [java.nio.file Files])
  (:require [clojure.java.io :as io]
            [clojure.test :as t]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]))

(defn pwd
  "return working dir of the JVM (cannot be changed once JVM is started)"
  []
  (.getCanonicalFile (clojure.java.io/file ".")))

(def GIT_ENV {"GIT_AUTHOR_NAME"     "Test User"
              "GIT_AUTHOR_EMAIL"    "user@domain.com"
              "GIT_AUTHOR_DATE"     "2019-01-16T22:22:22"
              "GIT_COMMITTER_NAME"  "Test User"
              "GIT_COMMITTER_EMAIL" "user@domain.com"
              "GIT_COMMITTER_DATE"  "2019-01-16T22:22:22"})

(defn repo-temp-dir []
  (Files/createTempDirectory
   (.toPath (io/as-file (System/getProperty "java.io.tmpdir")))
   "repo"
   (into-array java.nio.file.attribute.FileAttribute [])))

(defmacro shell!
  [& body]
  `(let [tmpdir# (repo-temp-dir)]
       (shell/with-sh-dir (str tmpdir#)
         (shell/with-sh-env GIT_ENV
           ~@body
           (str tmpdir#)))))


(defmacro shell-in-dir! [dir & body]
  `(let [dir# (str ~dir)]
     (shell/with-sh-dir dir#
       (shell/with-sh-env GIT_ENV ~@body dir#))))


(def deps-edn-path (-> "dummy-deps.edn" io/resource io/file str))


(defn sh [command]
  (assert shell/*sh-dir* "Can't run commands without a specified directory.")
  (let [result (shell/sh "/bin/bash" "-c" command)]
     (assert (-> result :exit zero?) (:err result))
     result))


(defn sh-p [cmd]
  (println "-----------------")
  (println cmd)
  (println (:out (sh cmd))))


(defn init! []  (sh "git init"))
(defn init-bare! []  (sh "git init --bare"))

(defn clone!
  ([url]
   (clone! url "."))
  ([url clone-dir]
   (sh (str "git clone " url " " clone-dir))))

(defn mkdir-p!
  "create a bunch of dirs all at the same time"
  [& dirs]
  (when dirs
    (sh (str "mkdir -p " (apply str (interpose "/" dirs))))))


(defn write-dummy-file-in! [& dirs]
  (apply mkdir-p! dirs)
  (let [command (str "echo \"some stuff: " (str (java.util.UUID/randomUUID)) "\" >> " (apply str (interpose "/" dirs)) (when dirs "/") (fs/temp-name "dummy" ".txt"))]
    (log/debug "will execute: " command)
    (sh command)))


(defn write-dummy-deps-edn-in! [& dirs]
  (apply mkdir-p! dirs)
  (let [dest (fs/with-cwd shell/*sh-dir*
               (-> dirs
                   vec
                   (conj "deps.edn")
                   (->> (apply fs/file))
                   str))
        command (str "cp " deps-edn-path " " dest)]
    ;(log/debug "will execute: "command)
    (sh command)))


(defn add! [] (sh "git add ."))

(defn remote-add! [remote-name remote-dir] (sh (format "git remote add %s %s" remote-name remote-dir)))

(defn commit! [] (sh "git commit -m \"Commit\" --allow-empty"))


(defn tag! [t] (sh (format "git tag -a -m \"R %s\" %s" t t)))

(defn list-remote-tags "Return a map of tag to ref" [remote]
  (let [lines (-> (sh (format "git ls-remote --tags %s" remote))
                   :out
                   clojure.string/split-lines)]
    (into {} (map (fn [line]
                    (let [[ref tag] (clojure.string/split line #"\s+")]
                      [(subs tag 10) ref]))
                  lines))))



(defn- current-branch []
  (-> (shell/sh "bash" "-c" (str "git branch | grep \\* | cut -d ' ' -f2"))
      :out
      (clojure.string/replace "\n" "")))

(defn- current-branch2 []
  (:out (sh "git rev-parse --abbrev-ref HEAD -- .")))

(defn remote-add! [name url]
  (let [current-branch (or (current-branch) "master")
        add (format "git remote add %s %s" name url)
        track (format "git branch --set-upstream-to=%s/%s %s" name current-branch current-branch)]
    (sh add)
   ; (sh track)
    ))

(defn checkout!
  ([branch-name]
   (sh (format "git checkout -b %s" branch-name)))
  ([branch-name remote-branch-name]
   (sh (format "git checkout -t -b %s %s" branch-name remote-branch-name))))

(defn dirty! [] (sh "echo \"Hello\" >> x && git add x"))


;; Create a bundle with: `git bundle create my.repo --all`
(defn clone-bundle! [bundle] (sh (format "git clone %s . -b master" (-> bundle io/resource io/as-file str))))
