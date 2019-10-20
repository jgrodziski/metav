
## Overview
Metav basically provides 3 operation:
- display : display the current version of the project/module.
- spit: spit project version info into files a specified locations.
- release: creates a new release of the project/module bumping 
  the project version and tagging the repo with the new version.

To do so Metav rests on a central api found in the namespace `metav.api`.

Each operation takes a context as parameter and runs its program based 
of the informations found there. A context is created with the function 
`metav.api/make-context`, the operations are found in:
- `metav.api/display!`
- `metav.api/spit!`
- `metav.api/release!`


### Context
The context from which operations derives their behaviour revolves mainly 
around two things. A working directory (WD) that must contain a deps.edn
file and the git state of the repo relative to that WD. From this WD 
metav  generates in the context an artefact name, recovers the current 
version of the project by parsing git tags that have that artefact name 
as prefix.   

The result of creating a context for a directory is a map, containing 
all the information metav needs to work. That data is found under keys 
namespaced `metav` in the context.

Options used in the operations are also merge in the context under
namespaced keys. For instance, release option are namespaced with 
`metav.release`.

#### Notes on the naming process.
To name a project metav takes several things into account.
- the name of the git root dir
- the git prefix leading to the root of the module
- the `:metav/use-full-name?` option
- the `:metav/module-name-override` option

If the git prefix is nil, the artefact's name is the git root dir or the 
override. If the git prefix isn't nil (case of a monorepo) the generated
artefact name is the dir names of the prefix separated by `"-"` or the 
override, optionnaly prefixed by the git root dir name in the case of
`:metav/use-full-name?` being true. 

### Display
Operation that prints the current versioning state of the project to stdout.

### Spit
Operation that spit the informations of a context into files. The 
versioning data is presented as follow in a clojurein edn format:

```clojure
{:module-name artefact-name ;; the artefact name ase discussed above
 :version (str version)
 :tag tag ;; (str artefact-name "-" version)
 :generated-at (iso-now)
 :path (if git-prefix git-prefix ".")}
```

### Release
Operation that will create a new version of a project/module. To 
do so it takes a context representing the current state of the project, 
creates the context we will find after the bump operation, tags 
the last commit with the new version and pushes the result. Optionally 
uses the spit operation, commits the spitted files then tags this commit
with the new version.

### CLI
Metav can be used from the command line. The 3 commands have each a separated main
ns to call them with the help of clojure. 

With metav in the classpath in a terminal:
- `clojure -m metav.display`
- `clojure -m metav.spit`
- `clojure -m metav.release`

### API example
```clojure
(require '[metav.api :as m-api])

;; Defines some option for metav
(def options {:metav/working-dir "my/projects/dir"     ;; dir from which metav bases its execution context
              :metav.release/without-sign true         ;; we're not signing git tags
              :metav.release/spit true                 ;; we want to spit project data before releasing
              :metav.spit/output-dir "resources"       ;; location where the spitted files go
              :metav.spit/namespace "metav.meta"       ;; namespace of the spitted files
              :metav.spit/formats #{:edn :clj :json}}) ;; formats in which the data is spitted
              
(def ctxt (m-api/make-context options)) ;; creates the execution context with the options

(def ctxt-after-release (m-api/release! ctxt-A1)) ;; actually perform the release.
```

## Options description
The full range of options that can be passed to metav operations.

### Context
- `:metav/working-dir` : directory from which the execution context will
  be made. When unspecified will be set to the current working directory
  of the running jvm.
- `:metav/version-scheme`: `#{:semver :maven}`, defaults to `:semver`.
- `:metav/min-sha-length`: `integer?`, the minimum size the commit hash 
  used in dirty versions. Defaults to `4`.
- `:metav/use-full-name?`: `boolean?`, controls the way the metav names 
  the project/module. Defaults to `false`. 
- `:metav/module-name-override`: `string?`, override the name generated 
  by metav. No defaults.

### Display
- `:metav.display/output-format`: `#{:edn :json :tab}`, the format used 
  to display the project's versioning data. Defaults to `:tab`.

### Spit
- `:metav.spit/output-dir`: `::u/non-empty-str`, the directory into 
  which files will be spitted. Defaults to `"resources"`.
- `:metav.spit/namespace`: `string?`, the namespace for the spitted 
  files. Defaults to `"meta"`.
- `:metav.spit/formats`: `(s/coll-of #{:clj :cljc :cljs :edn :json} :kind set?))`,
  the formats allowed for the spit operations in keyword form. Defaults 
  to `#{:edn}`.
- `:metav.spit/template`: `::u/resource-path`, mustache template that 
  must be in the classpath to be used as a java resource. No Defaults.
- `:metav.spit/rendering-output`: `::u/non-empty-str`, a path for the 
  spitted infos rendered with the template. No Defaults.

### Release

- `:metav.release/level`: `#{:alpha :beta :major :minor :patch :rc :release}`,
  the bump levels available, must be correlated with `:metav/version-scheme`.
  Defaults to `:patch`.
- `:metav.release/without-sign`: `boolean?`, whether version git tags 
  won't be signed with gpg. Defaults to `false`.
- `:metav.release/without-push`: `boolean?`, whether we don't want to push 
  after tagging a new version.  Defaults to `false`.
- `:metav.release/spit`: `boolean?`, whether we want to spit versioning 
  data before releasing.  Defaults to `false`.
