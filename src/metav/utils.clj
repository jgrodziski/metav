(ns metav.utils
  (:require
    [clojure.spec.alpha :as s]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [me.raynes.fs :as fs]))


(defn pwd []
  (str fs/*cwd*))


(defn assoc-computed [context k f]
  (assoc context k (f context)))

;;----------------------------------------------------------------------------------------------------------------------
;; Spec functions
;;----------------------------------------------------------------------------------------------------------------------
(s/def ::non-empty-str (s/and string? (complement empty?)))

(defn resource? [p]
  (-> p io/resource fs/exists?))

(s/def ::resource-path (s/and ::non-empty-str
                              resource?))


(defn ancestor? [path possible-descendant]
  (string/starts-with? (str (fs/normalized possible-descendant))
                       (str (fs/normalized path))))

;;----------------------------------------------------------------------------------------------------------------------
;; Error handling
;;----------------------------------------------------------------------------------------------------------------------
(defmacro check
  "Similar to clojure's `assert` but always on."
  ([x]
   `(when-not ~x
      (throw (ex-info (str "Check failed: " (pr-str '~x))
                      {:type :check-failed}))))
  ([x message]
   `(when-not ~x
      (throw (ex-info (str "Check failed: " ~message "\n" (pr-str '~x))
                      {:type :check-failed})))))


(defn check-spec
  "Similar to `clojure.spec.alpha/assert` but always on."
  [spec x]
  (if (s/valid? spec x)
    x
    (let [ed (assoc (s/explain-data spec  x)
               ::failure :assertion-failed)
          msg (str "Spec assertion for spec `" spec "` failed\n"
                   (with-out-str (s/explain-out ed)))
          err (ex-info
                msg
                ed)]
      (log/error err msg)
      (throw err))))

;;----------------------------------------------------------------------------------------------------------------------
;; Options handling
;;----------------------------------------------------------------------------------------------------------------------
(defmacro ensure-keys [m & kvs]
  (assert (even? (count kvs)))
  (let [res (gensym "res")]
    `(let [~res ~m
           ~@(apply concat (for [[k v] (partition 2 kvs)]
                             `[~res (if (contains? ~res ~k)
                                        ~res
                                        (assoc ~res ~k ~v))]))]
       ~res)))


(defmacro ensure-key [m k v]
  (list `ensure-keys m k v))


(defn merge-defaults [m defaults]
  (reduce (fn [acc k]
            (ensure-key acc k (get defaults k)))
          m
          (keys defaults)))


(defn merge&validate [context defaults spec]
  (-> context
      (merge-defaults defaults)
      (->> (check-spec spec))))

;;----------------------------------------------------------------------------------------------------------------------
;;----------------------------------------------------------------------------------------------------------------------
(defn side-effect-from-context! [context f!]
  (f! context)
  context)