(ns metav.version.protocols
  "Handle abstract versions as a set of malleable components")

(defprotocol SCMHosted
  (tag [this])
  (distance [this])
  (sha [this])
  (dirty? [this]))

(defprotocol Bumpable
  (bump [this level]))
