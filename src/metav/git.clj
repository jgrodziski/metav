(ns metav.git
  (:require [clojure.string :as string]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]
            [metav.git :as git]))

(def ^:dynamic *prefix* "v")
(def ^:dynamic *min-sha-length* 4)
(def ^:dynamic *dirty-mark* "DIRTY")

(def unix-git-command "git")

(def windows? (some->> (System/getProperty "os.name")
                       (re-matches #"(?i).*windows.*")))

(defn abort
  "Print msg to standard err and exit with a value of 1."
  [& msg]
  (binding [*out* *err*];make println print to stderr
    (when (seq msg) (apply println msg))
    (System/exit 1)))

(def ^:private find-windows-git
  (memoize
    (fn []
      (let [{:keys [exit out err]} (shell/sh "where.exe" "git.exe")]
        (if-not (zero? exit)
          (abort (format (str "Can't determine location of git.exe: 'where git.exe' returned %d.\n"
                                   "stdout: %s\n stderr: %s")
                              exit out err))
          (string/trim out))))))

(defn- git-exe []
  (if windows?
    (find-windows-git)
    unix-git-command))

(defn- git-command
  [& arguments]
  (let [cmd (conj arguments (git-exe))
        _ (log/debug "Will execute in shell: " (apply str (interpose " " cmd)))
        {:keys [exit out err]} (apply shell/sh cmd)]
    (if (zero? exit)
      (string/split-lines out)
      (do (log/error err) nil))))

(defn- git-in-dir [repo-dir & arguments]
  (if repo-dir
      (apply git-command "-C" repo-dir arguments);;apply is used to preserve the variadic arguments between function call
      (apply git-command arguments)))

(defn- inside-work-tree?
  "returns true if inside a git work tree"
  []
  (= "true" (first (git-command "rev-parse" "--is-inside-work-tree"))))

(defn toplevel
  "return the toplevel path as a string on the local filesystem corresponding to the dir containing the .git dir"
  ([] (toplevel nil))
  ([repo-dir]
   (first (git-in-dir repo-dir "rev-parse" "--show-toplevel"))))

(defn prefix
  "return the prefix (dir path relative to toplevel git dir).
  When invoked from a subdirectory, show the path of the current directory relative to the top-level directory."
  ([] (prefix nil))
  ([repo-dir]
   (first (git-in-dir repo-dir "rev-parse" "--show-prefix"))))

(defn- root-distance
  ([] (root-distance nil))
  ([repo-dir] (count (git-in-dir repo-dir "rev-list" "HEAD"))))

(defn- git-status
  ([] (git-status nil))
  ([repo-dir]
   (let [status-args ["status" "-b" "--porcelain"]]
     (if (nil? repo-dir)
       (apply git-in-dir repo-dir status-args)
       (apply git-in-dir repo-dir (conj status-args repo-dir))))))

(defn assert-committed?
  ([] (assert-committed nil))
  ([repo-dir]
   (when (re-find #"Changes (not staged for commit|to be committed)" (apply str (interpose " " (git-in-dir repo-dir "status"))))
     (throw (Exception. (str "Uncommitted changes in " repo-dir " git directory."))))))

(defn describe
  ([prefix min-sha-length] (git/describe nil prefix min-sha-length))
  ([repo-dir prefix min-sha-length] (git-in-dir repo-dir "describe" "--long" "--match"
                                                (str prefix "*.*")
                                                (format "--abbrev=%d" min-sha-length)
                                                (str "--dirty=-" *dirty-mark*)
                                                "--always")))

(defn tag! [v & {:keys [prefix sign] :or {prefix *prefix* sign "--sign"}}]
  (apply git-command (filter identity ["tag" sign "--annotate"
                                       "--message" "Automated metav release" (str prefix v)])))

(defn commit!
  "commit with message"
  ([msg] (commit! nil msg))
  ([repo-dir msg]
   (git-in-dir repo-dir "commit" "-m" msg)))

(defn push!
  ([] (push! nil))
  ([repo-dir] (git-in-dir repo-dir "push")))

(defn git-dir-opt [repo-dir]
  "--git-dir" (str repo-dir "/.git"))

(defn any-commits?
  "return whether the repo has any commits in it"
  ([] (any-commits? nil))
  ([repo-dir]
   (git-in-dir repo-dir "log")))

(defn working-copy-description
  "return the git working copy description as [base distance sha dirty?]"
  ([] (working-copy-description nil))
  ([repo-dir & {:keys [prefix min-sha-length]
                :or {prefix *prefix* min-sha-length *min-sha-length*}}]
   (when (git/any-commits? repo-dir)
     (let [re0 (re-pattern (format "^%s(.+)-(\\d+)-g([^\\-]{%d,})?(?:-(%s))?$"
                                   prefix min-sha-length *dirty-mark*))
           re1 (re-pattern (format "^(Z)?(Z)?([a-z0-9]{%d,})(?:-(%s))?$" ; fallback when no matching tag
                                   min-sha-length *dirty-mark*))]
       (when-let [v (first (git/describe repo-dir prefix min-sha-length))]
         (let [[_ base distance sha dirty] (or (re-find re0 v) (re-find re1 v))
               distance (or (when distance (Integer/parseInt distance)) (root-distance repo-dir))]
           ;;(prn "working copy description v" v " re-find re0 " (re-find re0 v) " re-f ind re1 " (re-find re1 v))
           (log/debug "working copy description: [" base distance sha (boolean dirty) "] {:prefix " prefix "}" )
           [base distance sha (boolean dirty)]))))))

(defn working-copy-state
  "return the git working copy state with :status and :describe keys"
  [& {:keys [prefix min-sha-length] :or {prefix *prefix* min-sha-length *min-sha-length*}}]
  (when-let [status (git-status)]
    {:status {:tracking (filter #(re-find #"^##\s" %) status)
              :files (remove empty? (remove #(re-find #"^##\s" %) status))}
     :describe (first (git/describe prefix min-sha-length))}))
