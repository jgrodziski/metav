(ns metav.domain.tag
  (:require [metav.domain.version.protocols :refer [to-vector]]
            [metav.domain.version.semver :refer [version]]))

(def DEFAULT-FORMAT-STRING-STANDALONE-REPO "v%2$s.%3$s.%4$s")
(def DEFAULT-FORMAT-STRING-MULTIREPO       "%1$s-%2$s.%3$s.%4$s")

(defn format
  ([context]
   (format (:metav/tag-format-string context)
           ;(:metav/tag-parse-re context)
                                        ;[major minor patch distance sha dirty?]
           ;artefact-name full-name definitive-module-name
           ))
  ([module-name version]
   (format DEFAULT-FORMAT-STRING-STANDALONE-REPO module-name version))
  ([format-string module-name version]
   (apply format format-string module-name (to-vector version)))
  ([module-name major minor patch distance sha dirty?]
   (format DEFAULT-FORMAT-STRING-STANDALONE-REPO module-name major minor patch distance sha dirty?))
  ([format-string module-name major minor patch distance sha dirty?]
   (format format-string module-name major minor patch distance sha dirty?)))

(defn parse
  "Return a vector with [module-name major minor patch distance sha dirty?]"
  [re s]
  )
