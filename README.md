# Metav

Metav is a library that helps the release and versioning process of Clojure projects, particularly the one using [tools.deps](https://github.com/clojure/tools.deps.alpha) and a _Monorepo_ style (see [Rationale](#rationale)).

<div id="TOC">
<ul> 
<li><a href="#installation">Installation</a></li>
<li><a href="#usage">Usage</a><ul>
<li><a href="#display-current-version">Display current version</a></li>
<li><a href="#release">Release</a></li>
<li><a href="#spit-current-versioning">Spit current versioning</a></li>
</ul></li>
<li><a href="#rationale">Rationale</a>
<ul>
<li><a href="#release-semantic">Release semantic</a></li>
<li><a href="#change-level-major-minor-patch">Change level (major, minor, patch)</a></li>
<li><a href="#repository-organization">Repository organization</a></li>
<li><a href="#version">Version</a></li>
</ul></li>
<li><a href="#behavior">Behavior</a>
<ul>
<li><a href="#version-bumping">Version bumping</a></li>
<li><a href="#module-naming">Module Naming</a></li>
<li><a href="#tagging-behavior">Tagging behavior</a></li>
<li><a href="#meta-management">Meta management</a></li>
<li><a href="#metav-interface">Metav interface</a></li>
</ul></li>
<li><a href="#license">License</a></li>
</ul></li>
</ul>
</div>

# Installation

Using tools.deps, add several alias in your `deps.edn` for each main task (display, spit, release) like this:

```clojure
{:aliases {:metav {:extra-deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "63b8286e5c8c0513431e8024a7d2f9a57bc2c18b"}}}
           :artifact-name {:extra-deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "63b8286e5c8c0513431e8024a7d2f9a57bc2c18b"}}
                           :main-opts ["-m" "metav.display"]}
           :release {:extra-deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "63b8286e5c8c0513431e8024a7d2f9a57bc2c18b"}}
                     :main-opts ["-m" "metav.release"]}}}
```

# Usage

## Display module's name and current version

One liner:
```
clj -Sdeps '{:deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "63b8286e5c8c0513431e8024a7d2f9a57bc2c18b"}}}' -m metav.display
```

If you've installed Metav's dependency in `deps.edn` like in the above Installation section, just run:
```
clj -A:artifact-name
```
You should get something like:
```
myawesomesys-backend        1.3.4
```
The module name is deduced from the path: each directory name from the toplevel to the module dir is concatenated in the module name separated with a hyphen ('-'). Example: a module sitting in the directory `/myawesomesys/backend` would automatically give the module name `myawesomesys-backend`. 
The tab character between the module name and version makes it easy to use `cut -f1` and `cut -f2` to extract the data in shell script. 

## Release

_Release_ is the process invoked by the developer when a code related to a change is ready for prime time, hence releasable. The release process does the following:
- **Check** everything is committed (no untracked or uncommitted file(s))
- **Bump** the current version according to the release level of the change (major, minor or patch)
- **Tag** the repo with that version. In case of monorepo, prefix the version with the module name (automatically deduced from the module's path or provided)
- **Push** the tag

One liner:
```
clj -Sdeps '{:deps {jgrodziski/metav {:git/url "https://github.com/jgrodziski/metav.git" :sha "63b8286e5c8c0513431e8024a7d2f9a57bc2c18b"}}}' -m metav.release
```

If you've installed Metav's dependency in `deps.edn` like in the above Installation section, just run:
```
clj -A:release minor
```
Will execute the _release_ process described above, the tag used for the release is then printed in the standard output.


## Spit current versioning in a file

TODO: that feature is missing for the moment.

```
clj -A:metav -m metav.spit
```

# Rationale

**At every moment we should be able to link a SCM hash to a software's binary artefact and also the inverse: link a binary artefact to a point in the SCM tree.** 

__The idea is to drive the version from git instead of the other way around and to accomodate a Monorepo style organization.__

## Release semantic

_Release_ means some source code changes in one or several commits are ready to be "published" in the repository for later deployment. The _Release_ process assigns a version number, tags the repo with it and push the changes. The _Release_ task is invoked by developer when she considers changes in source code are ready. Pushing binary artefact (JAR, docker image) is out of the scope of the _Release_ process and should be the responsibility of the CI system.


## Change level (major, minor, patch)

When releasing, developer indicates the characteristic of the changes regarding the breaks potentially introduced (*major* level change), whether new features were pushed (*minor* level with no breaking change) or just a fix with no new features nor breaking changes (_patch_ level). The _Release_ process takes care of dealing with the SCM and version number to left the developer only decides what she's releasing to the world. 

## Repository organization

SCM repository organization is important, with many decisions to make: mono or multirepos, modules slicing, links with the CI and build process. Monorepos are a popular way of organizing source code at the moment to promote better code sharing behavior, knowledge spreading, refactoring, etc. (see the article ["Monorepos and the fallacy of scale"](https://presumably.de/monorepos-and-the-fallacy-of-scale.html)). **The library is intended to accomodate Monorepos and Multirepos style of organization**, in case of _Monorepos_ style Metav's tagging behavior ensures isolation between components living in the same repo. Many tools implicitly depends on having a dedicated repository per component, in our case the way we manage the version and release from the source code should be independant of whether the source code is in a dedicated repo (Multirepos) or a shared one (Monorepos).

Monorepo layout makes it difficult to tag using only a version as several modules can collides, the used solution is to prefix the tag name with the module name then the version like so: `sys-container-version`. The annotation message of the tag can also contain some metadata in the form of an EDN data structure.

## Version

Each version should gives a clear semantic about the content of the change, [Semantic Versioning](https://semver.org) is a great way to do that.
I'm fond of using git tags to denote the current version of a component whether we use a Monorepo or a Multirepo.

Extract from the [semver](https://semver.org) website:

> Given a version number MAJOR.MINOR.PATCH, increment the:

> - MAJOR version when you make incompatible API changes,
> - MINOR version when you add functionality in a backwards-compatible manner, and
> - PATCH version when you make backwards-compatible bug fixes.
> Additional labels for pre-release and build metadata are available as extensions to the MAJOR.MINOR.PATCH format.


# Behavior

> __Every artifact should be reproduceable from the source code hash ([git reference](https://git-scm.com/book/en/v2/Git-Internals-Git-References))__

## Version bumping

Version is deduced from the current state of the SCM working copy: 

- is the source code on a tag? version is `1.5.2` 
- on a commit made after the tag? (possibly several commits, compute the distance from last tag and use it as patch number) version is `1.5.2+f34b91` (use the commit hash in version number)
- with uncommitted change (DIRTY state)? `1.5.2-f34b91-DIRTY`

The version is never persisted somewhere in source code to avoid any desynchronisation between SCM state and version number. _However_, the library can optionaly spit the metadata (module name and version) in file to be included in an artefact during the build process.

**The SCM state drives the version.**

**Version uses the Semantic Versioning scheme.**

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

## Meta management

Metadata, like module name and version, should be deduced from the SCM and include in the binary artefact (JAR, docker image) but never commited as file along the source code to avoid any desynchronisation. Metadata file is called `meta.edn`.

Metadata are:

- Module name
- version number
- timestamp
- Hash related to the version

See [spit function](#spit).

## Metav interface

### Display Module name and Version

### Release changes

### Spit meta information (module name and version)





# License

Copyright © 2019 Jeremie Grodziski

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
