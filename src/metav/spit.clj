(ns metav.spit
  (:require [metav.metadata :refer [invocation-context metadata-as-code metadata-as-edn]]
            [metav.git :as git]
            [metav.spit-cli :refer [parse-formats validate-args exit]]
            [clojure.java.io :refer [file]]
            [clojure.data.json :as json]
            [me.raynes.fs :as fs]
            [clojure.string :as str]))

(defn metafile! [output-dir namespace format]
  (fs/with-cwd output-dir
    (let [ns-file (fs/ns-path namespace)
          parent (fs/parent ns-file)
          name (fs/name ns-file)
          _ (fs/extension ns-file)]
      (fs/mkdirs parent)
      (file parent (str name "." format)))))

(defmulti spit-file! (fn [invocation-context _] (:format invocation-context)))

(defmethod spit-file! "edn" [{:keys [working-dir output-dir namespace format] :as invocation-context} version]
  (let [metafile (metafile! (str working-dir "/" output-dir) namespace format)]
    (spit metafile (pr-str (metadata-as-edn invocation-context)))
    (str metafile)))

(defmethod spit-file! "json" [{:keys [working-dir output-dir namespace format] :as invocation-context} version]
  (let [metafile (metafile! (str working-dir "/" output-dir) namespace format)]
    (spit metafile (json/write-str (metadata-as-edn invocation-context)))
    (str metafile)))

(defmethod spit-file! :default [{:keys [working-dir output-dir namespace format] :as invocation-context} version];default are cljs,clj and cljc
  (let [metafile (metafile! (str working-dir "/" output-dir) namespace format)]
    (spit metafile (metadata-as-code invocation-context))
    (str metafile)))


(defn spit-files!
  [{:keys [namespace formats module-name-override working-dir] :as invocation-context} version];invocation from release, the next version is given as arguments
  (vec (map (fn [format]
              (spit-file! (merge invocation-context {:format format}) version))
            (parse-formats formats))))

(defn -main
  [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)
        {:keys [version] :as invocation-context} (invocation-context options)]
    (when exit-message
      (exit (if ok? 0 1) exit-message))
    (spit-files! invocation-context version);spit files invoked from CLI deduce the current version from git state
    (if (:verbose options)
      (print (json/write-str (metadata-as-edn invocation-context))))
    (shutdown-agents)))
