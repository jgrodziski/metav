(ns metav.spit
  (:require [metav.display :refer [version module-name tag]]
            [metav.git :as git]
            [clojure.data.json :as json]))

(defn metadata
  "return a map of the repo metadata: version, name, path, etc."
  [working-dir tag version]
  {:module-name (module-name working-dir)
   :tag tag
   :version (str version)
   :sha (git/last-sha working-dir)
   :path (git/prefix working-dir)})

(defn metadata-json-str
  "returns a string of the repo metadata as JSON to be included in the message of the annotated tag"
  [working-dir tag version]
  (json/write-str (metadata working-dir tag version)))

(defn -main
  ""
  [& args]
  )
