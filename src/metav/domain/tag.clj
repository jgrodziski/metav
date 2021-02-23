(ns metav.domain.tag
  (:require [metav.domain.version.protocols :refer [to-vector]]
            [metav.domain.version.semver :refer [version]])
  (:refer-clojure :exclude [format]))

(def DEFAULT-FORMAT-STRING-STANDALONE-REPO "v%1$s.%2$s.%3$s")
(def DEFAULT-REGEX-STANDALONE-REPO         #"v(?<major>.*)\.(?<minor>.*)\.(?<patch>.*)")

(def DEFAULT-FORMAT-STRING-MONOREPO       "%7$s-%1$s.%2$s.%3$s")
(def DEFAULT-REGEX-MONOREPO               #"(?<artefactname>.*-.*)-(?<major>.*)\.(?<minor>.*)\.(?<patch>.*)")

(defn format
  ([version artefact-name]
   (format DEFAULT-FORMAT-STRING-STANDALONE-REPO version artefact-name ))
  ([format-string version artefact-name]
   (apply format format-string (conj (to-vector version) artefact-name)))
  ([major minor patch distance sha dirty? artefact-name]
   (format DEFAULT-FORMAT-STRING-STANDALONE-REPO major minor patch distance sha dirty? artefact-name))
  ([format-string major minor patch distance sha dirty? artefact-name]
   (clojure.core/format format-string major minor patch distance sha dirty? artefact-name)))

(defn- group [matcher group-name]
  (try
    (.group matcher group-name)
    ;IllegalArgumentException when no group exist with that name in the regex pattern
    (catch IllegalArgumentException iae)))

(defn parse
  "Return the groups from the regex in a map, regex should use named group like (?<name>.*) modulename, distance, sha and dirty being optional (nil if missing from the tag)"
  ([s]
   (parse DEFAULT-REGEX-STANDALONE-REPO s))
  ([re s]
   (let [m (re-matcher re s)]
     (when (.matches m)
       (into {} (filter val {:module-name (group m "artefactname")
                             :major       (group m "major")
                             :minor       (group m "minor")
                             :patch       (group m "patch")
                             :distance    (group m "distance")
                             :sha         (group m "sha")
                             :dirty?      (group m "dirty")}))))))

(defn ^:deprecated version-prefix
  "Version prefix in git tags, \"v\" in dedicated repos \"artefact-name-\" in monorepos."
  [context]
  (let [{:metav/keys [git-prefix artefact-name]} context]
    (if git-prefix
      (str artefact-name "-")
      "v")))


(defn tag
  "Makes a tag name from a context using the version prefix and the version number."
  ([context]
   (tag (:metav/monorepo? context) (:metav/version context) (:metav/artefact-name context)))
  ([monorepo? version artefact-name]
   (if monorepo?
     (format DEFAULT-FORMAT-STRING-MONOREPO version artefact-name)
     (format DEFAULT-FORMAT-STRING-STANDALONE-REPO version artefact-name))))
