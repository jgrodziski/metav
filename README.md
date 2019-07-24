
# Metav

> “The assumption of an absolute determinism is the essential foundation of every scientific enquiry.” 
> ― _Max Planck_

Metav is a library that helps the release and versioning process of Clojure projects, particularly the one using [tools.deps](https://github.com/clojure/tools.deps.alpha) and a _Monorepo_ style (see [Rationale](#rationale)).

[![Clojars Project](https://img.shields.io/clojars/v/metav.svg)](https://clojars.org/metav)
[![cljdoc badge](https://cljdoc.org/badge/metav/metav)](https://cljdoc.org/d/metav/metav/CURRENT)
[![CircleCI](https://circleci.com/gh/jgrodziski/metav/tree/master.svg?style=svg)](https://circleci.com/gh/jgrodziski/metav/tree/master)
[![License](https://img.shields.io/badge/License-EPL%201.0-blue.svg)](https://opensource.org/licenses/EPL-1.0)

<div id="TOC">
<ul> 
<li><a href="#installation">Installation</a></li>
<li><a href="#usage">Usage</a><ul>
<li><a href="#display-modules-name-and-current-version">Display current version</a></li>
<li><a href="#release">Release</a></li>
<li><a href="#spit-or-render-current-version-in-a-file">Spit or render current version in a file</a></li>
<li><a href="#init-an-existing-codebase-with-a-specific-version">Init an existing codebase with a specific version</a></li>
</ul></li>
<li><a href="#behavior">Behavior</a>
<ul>
<li><a href="#version-bumping">Version bumping</a></li>
<li><a href="#module-naming">Module Naming</a></li>
<li><a href="#tagging-behavior">Tagging behavior</a></li>
<li><a href="#meta-management">Meta management</a></li>
<li><a href="#metav-interface">Metav interface</a></li>
</ul></li>
<li><a href="#rationale">Rationale</a>
<ul>
<li><a href="#release-semantic">Release semantic</a></li>
<li><a href="#change-level-major-minor-patch">Change level (major, minor, patch)</a></li>
<li><a href="#repository-organization">Repository organization</a></li>
<li><a href="#version">Version</a></li>
<li><a href="#inspiration">Inspiration</a></li>
</ul></li>
<li><a href="#license">License</a></li>
</ul></li>
</ul>
</div>

# Installation

Latest version: 1.5.3

[deps.edn](https://clojure.org/guides/deps_and_cli) dependency information:
```
{metav {:mvn/version "1.5.3"}}
```

Using [tools.deps](https://github.com/clojure/tools.deps.alpha), add several alias in your `deps.edn` for each main task (display, spit, release) like this with git ref:

```clojure
{:aliases {:metav {:extra-deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "42ed18317ec843813e5084b4a4b616eb03e9679e"}}}
           :artifact-name {:extra-deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "42ed18317ec843813e5084b4a4b616eb03e9679e"}}
                           :main-opts ["-m" "metav.display"]}
           :release {:extra-deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "42ed18317ec843813e5084b4a4b616eb03e9679e"}}
                     :main-opts ["-m" "metav.release"]}}}
```

Or using the clojars version `{metav {:mvn/version "1.5.2"}}` or use `{metav {:mvn/version "LATEST"}}` to always living on the edge:
```clojure
{:aliases {:metav {:extra-deps {metav {:mvn/version "LATEST"}}}
           :artifact-name {:extra-deps {metav {:mvn/version "LATEST"}}
                           :main-opts ["-m" "metav.display"]}
           :release {:extra-deps {metav {:mvn/version "LATEST"}}
                     :main-opts ["-m" "metav.release"]}
           :spit     {:extra-deps {metav {:mvn/version "LATEST"}}
                      :main-opts ["-m" "metav.spit"
                                  "--output-dir" "src"
                                  "--namespace" "yournamespace.meta"
                                  "--formats" "clj"]}}}
```

# Usage

## Display module's name and current version

One liner:
```
clj -Sdeps '{:deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "42ed18317ec843813e5084b4a4b616eb03e9679e"}}}' -m metav.display
```

If you've installed Metav's dependency in `deps.edn` like in the above Installation section, just run:
```
clj -A:artifact-name
```
You should get something like:
```
myawesomesys-backend        1.3.4
```
The module name is deduced from the path: each directory name from the toplevel to the module dir is concatenated in the module name separated with a hyphen ('-'). Example: a module sitting in the directory `/myawesomesys/backend` would automatically give the module name `myawesomesys-backend`. You also have the possibility to override the module name with the following option (available on the three tasks: display, release, spit):
```
  -r, --module-name-override MODULE-NAME Module Name Override
```
The tab character between the module name and version makes it easy to use `cut -f1` and `cut -f2` to extract the data in shell script. 

To avoid problem with logging libraries that can mess up the stdout and make it difficult to extract the module name and version, you can output the metadata in a file with the `spit` function and then extract the values to build the artifact name. Example:

```shell
clj -A:metav -m "metav.spit" "-o" "target" "-f" "json"
ARTIFACT_ID=$(cat target/meta.json | jq -r '."module-name"')
ARTIFACT_VERSION=$(cat target/meta.json | jq -r '."version"')
ARTIFACT_TAG=$(cat target/meta.json | jq -r '."tag"')
JAR_FILENAME="$ARTIFACT_TAG.jar"

```

## Release

_Release_ is the process invoked by the developer when a code related to a change is ready for prime time, hence releasable. The release process does the following:
- **Check** everything is committed (no untracked or uncommitted file(s) otherwise the release process is aborted)
- **Bump** the current version according to the release level of the change (major, minor or patch)
- **[Optionaly: Spit and Commit metadata]**: Spit metadata in file(s) (version, tag, timestamp, module-name, etc.) with the `-s, --spit` option flag (presence means spitting metadata).
- **Tag** the repo with that version. In case of monorepo, prefix the version with the module name (automatically deduced from the module's path or provided). The git tag can be signed with GPG or not, default behavior is to sign the tag.
- **Push** the tag

One liner:
```
clj -Sdeps '{:deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "42ed18317ec843813e5084b4a4b616eb03e9679e"}}}' -m metav.release
```

If you've installed Metav's dependency in `deps.edn` like in the above Installation section, just run:
```
clj -A:release minor
```
Will execute the _release_ process described above, the tag used for the release is then printed in the standard output.

The `release` can also output metadata files like the `spit` function does, the CLI options are the same than `spit` there is a boolean flag option indicating that the `spit` is done (default to `false`).

The `release` usage is:
```
Metav's "release" function does the following:
  - assert the command is invoked with a deps.edn in the working directory
  - assert everything is committed (no untracked or uncommitted files).
  - bump the version
  - [optional: spit and commit the version metadata (module-name, tag, version, sha, timestamp) in file(s)]
  - tag the repo with the version prefixed by the module-name in cas of a monorepo (the git tag can be signed with GPG or not, default behavior is to sign the tag)
  - push everything

Usage: metav.release [options] <level>
with <level>: major, minor or patch

Options:
      --without-sign                      Should the git tag used for release be signed with the current user's GPG key configured with git
  -s, --spit                              Indicates the release process should spit the metadata file as with the "spit" task, in that case the spit options must be provided
  -o, --output-dir DIR_PATH  resources    Output Directory
  -n, --namespace NS         meta         Namespace used in code output
  -f, --formats FORMATS      edn          Comma-separated list of output formats (clj, cljc, cljs, edn, json)
  -t, --template TEMPLATE                 Template used for rendering (must follows mustache format, spitted data is available during template rendering)
  -d, --rendering-output RENDERING-OUTPUT File to render template in
  -r, --module-name-override MODULE-NAME  Module Name Override
  -v, --verbose                           Verbose, output the metadata as json in stdout if the option is present
  -h, --help                              Help

```

## Spit or render current version in a file

The spit feature output the current state of the module in the repo in one or several files that can be directly Clojure source code (`clj`, `cljc` and `cljs` formats) or data literals structure like EDN or JSON (`edn` and `json` format).
You can also render a mustache template with the `-t` and `-d` option (see options below) to output a file with the metadata used in the template (like the version). A typical use case would be to link to a specific version of an artefact (like a link to a JS bundle in a HTML file).

### Example of _spitted_ output as source code

```clojure
(ns metav.meta)

(def module-name "metav")
(def path ".")
(def version "1.1.2")
(def tag "v1.1.2")
(def generated-at "2019-02-02T22:57:54Z")
```

### Usage

```
clj -A:metav -m metav.spit --output-dir src --namespace metav.meta -formats clj
;will output src/metav/meta.clj
;or
clj -A:metav -m metav.spit --output-dir resources --namespace meta -formats edn,json
;will output resources/meta.edn and resources.json
```

The `spit` usage help is (`clj -Ametav -m metav.spit --help`):

```
The spit function of Metav output module's metadata in files according the given formats among: clj, cljc, cljs, edn or json.
The metadata is composed of: module-name, tag, version, path, timestamp

Usage: metav.spit [options]

Options:
  -o, --output-dir DIR_PATH  resources    Output Directory
  -n, --namespace NS         meta         Namespace used in code output
  -f, --formats FORMATS      edn          Comma-separated list of output formats (clj, cljc, cljs, edn, json)
  -t, --template TEMPLATE                 Template used for rendering (must follows mustache format, spitted data is available during template rendering)
  -d, --rendering-output RENDERING-OUTPUT File to render template in
  -r, --module-name-override MODULE-NAME  Module Name Override
  -v, --verbose                           Verbose, output the metadata as json in stdout if the option is present
  -h, --help                              Help
```

## Init an existing codebase with a specific version

Set a git annotated tag with the current version with `git tag v1.7.5 -m "version 1.7.5"` (the `-m` flag is important to set the tag as an annotated one, otherwise metav would not see it), then you can release it as usual with the `release` feature, the next version will be set starting from the specific setted version.

# Behavior

> __Every artifact should be reproduceable from the source code hash ([git reference](https://git-scm.com/book/en/v2/Git-Internals-Git-References))__

## Version bumping

Version is deduced from the current state of the SCM working copy: 

- is the source code on a tag? version is `1.5.2` 
- on a commit made after the tag? (possibly several commits, compute the distance from last tag and use it as patch number) version is `1.5.2-f34b91` (use the commit hash in version number)
- with uncommitted change (DIRTY state)? `1.5.2-f34b91-DIRTY`

The version is never persisted somewhere in source code to avoid any desynchronisation between SCM state and version number. _However_, the library can optionaly spit the metadata (module name and version) in file to be included in an artefact during the build process.

* **The SCM state drives the version.**
* **Version uses the [Semantic Versioning scheme](https://semver.org).**

We never use SNAPSHOT in version number as it's difficult to know what's really inside the binary artefact.


## Module Naming

We believe repo layout should follows some convention regarding the system, container, component organization and relationships (following the [C4 model](https://c4model.com) for example, but any other layout should be possible). Hence the naming scheme should reflect that organization, if we take the same example used in the C4 model documentation, the folders in the monorepo should be:

- `Monorepo root`
    - `Internet_Banking` (_System_ in C4 model)
    - `Web_Application`
        - `Single_Page_Application`
        - `Mobile_App`
        - `API` (_Container_ in C4 model, actual project that delivers an artefact whose name is `Internet_Banking-API`)
            - `src/`
            - `test/`
            - `resources/`
            - `deps.edn`
        - `Database`
    - `Mainframe_Banking`
    - `Email`
    
Module's name is by default deduced from the repo path layout (but can also be overriden): each directory name from the toplevel to the module dir is concatenated in the module name separated with a hyphen ('-'). Example: a module sitting in the directory `/myawesomesys/backend` would automatically give the module name `myawesomesys-backend`. 
In case of a dedicated repo, Metav takes only the folder name containing the working copy (aka. containing the `.git` folder), e.g. if your repo sits in the `awesomerepo` dir then the module's name will be `awesomerepo`.  

## Tagging behavior

Each _release_ invocation tags the current SCM state with the following naming scheme: `system-container-version`.
The tagging function use [git annotated tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging) using the naming scheme describe previously, the message contains an EDN data structure described the module that is tagged: 

```clojure
{:module-name "Internet_banking-API" 
 :version "1.5.2"
 :path "Internet_Banking/API"
 :msg "Add new attachment feature in the message part of the system"}
``` 

The metadata in the tag message is stored as JSON and can later be extracted for use in shell script like so:
```
git tag -l --format %(contents:subject) v1.0.3 | jq '."module-name"'
```
Don't forget to start the command with a `noglob` if you use `zsh` as the `%(...)` will be interpreted otherwise.

## Meta management

Metadata, like module name and version, should be deduced from the SCM and included in the binary artefact (JAR, docker image). Metadata file can be named `meta.edn` for example.

Metadata are:

- Module name
- Version number
- Tag
- Timestamp
- Path in the repo

See [spit function](#spit-current-version-in-a-file).

# Rationale

* **SCM reference (hash) should give -> Artefact.**
* **Artefact should give -> SCM reference (Hash).** 
    * We should be able to link a SCM hash to a software's binary artefact and the inverse: link a binary artefact to a reference in the SCM tree.
* **Version is derived from git state instead of the other way around** (like a file versioned in the repo, with all the desynchronisation risks)
* **The library should accomodate a Monorepo style organization** where several modules (directory containing a `deps.edn` file) lives under a top level repository, hence mixing the version and tag in it.
* **Artifact construction from the source code SCM state should be deterministic.** An SCM reference should always give the same artefact.

## Release semantic

_Release_ means some source code changes in one or several commits are ready to be "published" in the repository for later deployment. The _Release_ process assigns a version number, tags the repo with it and push the changes. The _Release_ task is invoked by developer when she considers changes in source code are ready. Pushing binary artefact (JAR, docker image, etc.) somewhere is out of the scope of the _Release_ process and should be the responsibility of the CI system.


## Change level (major, minor, patch)

When releasing, developer indicates the characteristic of the changes regarding the breaks potentially introduced (*major* level change), whether new features were pushed (*minor* level with no breaking change) or just a fix with no new features nor breaking changes (_patch_ level). The _Release_ process takes care of dealing with the SCM and version number to left the developer only decides what she's releasing to the world. 

## Repository organization

SCM repository organization is important, with many decisions to make: mono or multirepos, modules slicing, links with the CI and build process. Monorepos are a popular way of organizing source code at the moment to promote better code sharing behavior, knowledge spreading, refactoring, etc. (see the article ["Monorepos and the fallacy of scale"](https://presumably.de/monorepos-and-the-fallacy-of-scale.html)).

**The library is intended to accomodate Monorepos and Multirepos style of organization**, in case of _Monorepos_ style Metav's tagging behavior ensures isolation between components living in the same repo. Many tools implicitly depends on having a dedicated repository per component, in our case the way we manage the version and release from the source code should be independant of whether the source code is in a dedicated repo (Multirepos) or a shared one (Monorepos).

Monorepo layout makes it difficult to tag using only a version as several modules versions can collide, the solution used by metav is to prefix the tag name with the module name then the version like so: `sys-container-version`. The annotation message of the tag can also contain some metadata in the form of an EDN data structure.

## Version

Each version should gives a clear semantic about the content of the change, [Semantic Versioning](https://semver.org) is a great way to do that.
I'm fond of using git tags to denote the current version of a component whether we use a Monorepo or a Multirepo.

Extract from the [semver](https://semver.org) website:

> Given a version number MAJOR.MINOR.PATCH, increment the:

> - MAJOR version when you make incompatible API changes,
> - MINOR version when you add functionality in a backwards-compatible manner, and
> - PATCH version when you make backwards-compatible bug fixes.
> Additional labels for pre-release and build metadata are available as extensions to the MAJOR.MINOR.PATCH format.

## Inspiration

Metav was inspired from these existing libraries in the Leiningen ecosystem:
- [lein-git-version](https://github.com/arrdem/lein-git-version)
- [lein-v](https://github.com/roomkey/lein-v) I used that one some times ago and Metav borrowed the SemVer and Maven version handling code.

The monorepo concern also has solutions like:
- [lein-modules](https://github.com/jcrossley3/lein-modules)
- [lein-monolith](https://github.com/amperity/lein-monolith)
and of course the [release task of lein](https://github.com/technomancy/leiningen/blob/master/doc/DEPLOY.md#releasing-simplified), all of that was the inception of Metav.


# License

Copyright © 2019 Jeremie Grodziski

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
