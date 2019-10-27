(ns metav.domain.version.protocols
  "Handle abstract versions as a set of malleable components")


(defprotocol SCMHosted
  (subversions [this])
  (tag [this])
  (distance [this])
  (sha [this])
  (dirty? [this]))


(defprotocol Bumpable
  (bump [this level]))
