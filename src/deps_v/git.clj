(ns deps-v.git
  (:require [clojure.string :as string]
            [clojure.java.shell :as shell]
            [clojure.tools.logging :as log]))

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
        {:keys [exit out err]} (apply shell/sh cmd)]
    (if (zero? exit)
      (string/split-lines out)
      (do (log/warn err) nil))))

(defn- root-distance
  []
  (count (git-command "rev-list" "HEAD")))

(defn- git-status []
  (git-command "status" "-b" "--porcelain"))

(defn- git-describe [prefix min-sha-length]
  (git-command "describe" "--long" "--match"
               (str prefix "*.*")
               (format "--abbrev=%d" min-sha-length)
               (str "--dirty=-" *dirty-mark*)
               "--always"))

(defn tag [v & {:keys [prefix sign] :or {prefix *prefix* sign "--sign"}}]
  (apply git-command (filter identity ["tag" sign "--annotate"
                                       "--message" "Automated lein-v release" (str prefix v)])))

(defn version
  [& {:keys [prefix min-sha-length]
      :or {prefix *prefix* min-sha-length *min-sha-length*}}]
  (let [re0 (re-pattern (format "^%s(.+)-(\\d+)-g([^\\-]{%d,})?(?:-(%s))?$"
                                prefix min-sha-length *dirty-mark*))
        re1 (re-pattern (format "^(Z)?(Z)?([a-z0-9]{%d,})(?:-(%s))?$" ; fallback when no matching tag
                                min-sha-length *dirty-mark*))]
    (when-let [v (first (git-describe prefix min-sha-length))]
      (let [[_ base distance sha dirty] (or (re-find re0 v) (re-find re1 v))]
        (let [distance (or (when distance (Integer/parseInt distance)) (root-distance))]
          [base distance sha (boolean dirty)])))))

(defn workspace-state [project & {:keys [prefix min-sha-length]
                                  :or {prefix *prefix* min-sha-length *min-sha-length*}}]
  (when-let [status (git-status)]
    {:status {:tracking (filter #(re-find #"^##\s" %) status)
              :files (remove empty? (remove #(re-find #"^##\s" %) status))}
     :describe (first (git-describe prefix min-sha-length))}))
