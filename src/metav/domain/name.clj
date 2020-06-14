(ns metav.domain.name)

(defn definitive-module-name
  "Choose the module name to be used between the one metav generates automatically or the override provided by the user."
  [context]
  (let [{:metav/keys [module-name-override module-name]} context]
    (or module-name-override module-name)))


(defn full-name
  "Full name constructed with the project name and the module name."
  [context]
  (let [{:metav/keys [git-prefix project-name definitive-module-name]} context]
    (if git-prefix
      (str project-name "-" definitive-module-name)
      definitive-module-name)))


(defn artefact-name
  "The name used to create tag and/or maven artifact name."
  [context]
  (if (get context :metav/use-full-name?)
    (:metav/full-name context)
    (:metav/definitive-module-name context)))
