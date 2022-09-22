(ns metav.domain.git
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(def ^:dynamic *prefix* "v")
(def ^:dynamic *min-sha-length* 8)
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
         (first (string/split-lines (string/trim out))))))))


(defn- git-exe []
  (if windows?
    (find-windows-git)
    unix-git-command))


(defn- git-command
  [& arguments]
  (let [cmd (conj arguments (git-exe))
        cmd-str (apply str (interpose " " cmd))
        _ (log/debug "Will execute in shell: " cmd-str)
        {:keys [exit out err] :as result} (apply shell/sh cmd)]
    (if (zero? exit)
      (string/split-lines out)
      (do
        (log/error (str "executed:\n" cmd-str
                        "\ngit error:\n" err))
        result))))


(defn git-in-dir [repo-dir & arguments]
  (if repo-dir
    (apply git-command (cons "-C" (cons repo-dir arguments)));;apply is used to preserve the variadic arguments between function call
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


(defn git-short-status
  ([] (git-short-status nil))
  ([repo-dir]
   (let [status-args ["status" "--short"]]
     (if (nil? repo-dir)
       (apply git-in-dir repo-dir status-args)
       (apply git-in-dir repo-dir (conj status-args repo-dir))))))


(defn- git-status
  ([] (git-status nil))
  ([repo-dir]
   (let [status-args ["status" "-b" "--porcelain"]]
     (if (nil? repo-dir)
       (apply git-in-dir repo-dir status-args)
       (apply git-in-dir repo-dir (conj status-args repo-dir))))))


(def status-codes #{"M" "A" "D" "R" "C" "U"})


(defn committed? [repo-dir]
  (let [paths (git-short-status repo-dir)
        uncommitted-pred #(re-find #"(M|A|D|R|C|U|\\?| )(M|A|D|R|C|U|\\?| ) .*" %)]
    (some uncommitted-pred paths)))


(defn assert-committed? [repo-dir]
  (when (committed? repo-dir)
    (let [error-msg-uncommitted (str "Untracked or uncommitted changes in " repo-dir " git directory (as stated by 'git status command'). Please add/commit your change to get a clean repo.")]
      (log/error error-msg-uncommitted)
      (throw (Exception. error-msg-uncommitted)))))


(defn latest-tag [repo-dir]
  (first (git-in-dir repo-dir "describe" "--abbrev=0")))


(defn describe
  ([prefix min-sha-length] (describe nil prefix min-sha-length))
  ([repo-dir prefix min-sha-length] (git-in-dir repo-dir "describe" "--long" "--match"
                                                (str prefix "*.*")
                                                (format "--abbrev=%d" min-sha-length)
                                                (str "--dirty=-" *dirty-mark*)
                                                "--always")))


(defn tag! [repo-dir tag metadata & {:keys [sign] :or {sign "--sign"}}]
  ;;tagging with git returns nothing in the std out, there is somethin only in case of failure
  (let [result (apply git-in-dir repo-dir (filter identity ["tag" sign "--annotate" "--message" metadata tag]))]
    (when (and result (map? result) (:exit result) (not (zero? (:exit result))))
      (throw (ex-info "Failure when trying to tag repository" {:tag tag :metadata metadata :sign? sign :repo-dir repo-dir :result result})))))


(defn add! [repo-dir & paths]
  (apply git-in-dir repo-dir "add" paths))


(defn commit!
  "commit with message"
  ([msg] (commit! nil msg))
  ([repo-dir msg]
   (git-in-dir repo-dir "commit" "-m" msg)))


(defn current-branch
  ([repo-dir]
   (-> (shell/sh "bash" "-c" (str "git -C " repo-dir " branch | grep \\* | cut -d ' ' -f2"))
       :out
       (clojure.string/replace "\n" ""))))


(comment (defn push!
           ([remote branch] (push! nil remote branch))
           ([repo-dir remote branch] (git-in-dir repo-dir "push"))))


(defn push!
  ([repo-dir]
   (git-in-dir repo-dir "push"))
  ([repo-dir tag] (git-in-dir repo-dir "push" "origin" tag)))


(defn git-dir-opt [repo-dir]
  "--git-dir" (str repo-dir "/.git"))


(defn any-commits?
  "return whether the repo has any commits in it"
  ;([] (any-commits? nil))
  ([repo-dir]
   (let [result (git-in-dir repo-dir "log")]
     (not (map? result)))));maps means an error occurs  during the git command


(defn tag-timestamp [working-dir tag]
  (first (git-in-dir working-dir "log" "-1" "--format=%aI" tag)))


(defn tag-message [working-dir tag]
  (git-in-dir working-dir "tag" "-l" "--format" "%(contents:subject)" tag))


(defn tag-verify [working-dir tag]
  (let [result (git-in-dir working-dir "tag" "-v" tag)]
    (if (and result (map? result) (:exit result) (not (zero? (:exit result))))
      (throw (ex-info (str "Can't verify tag " tag " with GPG signature in directory " working-dir) {:working-dir working-dir :tag tag :result result}))
      result)))

(defn list-remote-tags [remote]
  (when remote
    (let [lines (git-command "ls-remote" "--tags" remote)]
      (into {} (map (fn [line]
                      (let [[ref tag] (clojure.string/split line #"\s+")]
                        [(subs tag 10) ref]))
                    lines)))))

(defn last-sha [working-dir]
  (first (git-in-dir working-dir "rev-parse" "HEAD")))


(defn working-copy-description
  "return the git working copy description as [base distance sha dirty?]"
  ([] (working-copy-description nil))
  ([repo-dir & {:keys [prefix min-sha-length]
                :or {prefix *prefix* min-sha-length *min-sha-length*}}]
   (when (any-commits? repo-dir)
     (let [re0 (re-pattern (format "^%s(.+)-(\\d+)-g([^\\-]{%d,})?(?:-(%s))?$"
                                   prefix min-sha-length *dirty-mark*))
           re1 (re-pattern (format "^(Z)?(Z)?([a-z0-9]{%d,})(?:-(%s))?$" ; fallback when no matching tag
                                   min-sha-length *dirty-mark*))
           desc (describe repo-dir prefix min-sha-length)]
       (when-let [v (first desc)]
         (let [[_ base distance sha dirty] (or (re-find re0 v) (re-find re1 v))
               distance (or (when distance (Integer/parseInt distance)) (root-distance repo-dir))]
           ;(prn "desc" desc)
           ;(prn "working copy description v" v " re-find re0 " (re-find re0 v) " re-f ind re1 " (re-find re1 v))
           (log/debug "working copy description: [" base distance sha (boolean dirty) "] {:prefix " prefix "}")
           [base distance sha (boolean dirty)]))))))


(defn working-copy-state
  "return the git working copy state with :status and :describe keys"
  [& {:keys [prefix min-sha-length] :or {prefix *prefix* min-sha-length *min-sha-length*}}]
  (when-let [status (git-status)]
    {:status {:tracking (filter #(re-find #"^##\s" %) status)
              :files (remove empty? (remove #(re-find #"^##\s" %) status))}
     :describe (first (describe prefix min-sha-length))}))
