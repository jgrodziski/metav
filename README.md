# Metav

Metav is a library that helps the release and versioning process of Clojure projects, particularly the one using [tools.deps](https://github.com/clojure/tools.deps.alpha).

## Rationale

**At every moment we should be able to link a SCM hash to a software's binary artefact and also the inverse: link a binary artefact to a point in the SCM tree.** 

**The idea is to drive the version from git instead of the other way around.**

### Release semantic

_Release_ means some source code changes in one or several commits are ready to be "published" in the repository for later deployment. The _Release_ process assigns a version number, tags the repo with it and push the changes. The _Release_ task is invoked by developer when she considers changes in source code are ready. Pushing binary artefact (JAR, docker image) is out of the scope of the _Release_ process and should be the responsibility of the CI system.


### Change level (major, minor, patch)

When releasing, developer indicates the characteristic of the changes regarding the breaks potentially introduced (*major* level change), whether new features were pushed (*minor* level with no breaking change) or just a fix with no new features nor breaking changes (_patch_ level). The _Release_ process takes care of dealing with the SCM and version number to left the developer only decides what she's releasing to the world. 

### Repository organization

SCM repository organization is important, with many decisions to make: mono or multirepos, modules slicing, links with the CI and build process. Monorepos are a popular way of organizing source code at the moment to promote better code sharing behavior, knowledge spreading, refactoring, etc. (see the article ["Monorepos and the fallacy of scale"](https://presumably.de/monorepos-and-the-fallacy-of-scale.html)). **The library is intended to accomodate Monorepos and Multirepos style of organization**, in case of _Monorepos_ style Metav's tagging behavior ensures isolation between components living in the same repo.

### Version

Each version should gives a clear semantic about the content of the change, [Semantic Versioning](https://semver.org) is a great way to do that.

Extract from the [semver](https://semver.org) website:

> Given a version number MAJOR.MINOR.PATCH, increment the:

> - MAJOR version when you make incompatible API changes,
> - MINOR version when you add functionality in a backwards-compatible manner, and
> - PATCH version when you make backwards-compatible bug fixes.
> Additional labels for pre-release and build metadata are available as extensions to the MAJOR.MINOR.PATCH format.


## Behavior

Monorepos, or at least a reduced number of repos, brings better code sharing for the team. The way we manage the version and release from the source code should be independant of whether the source code is in a dedicated repo (Multirepos) or a shared one (Monorepos). 
Many tools implicitly depends on having a dedicated repository per component. I'm fond of using git tags to denote the current version.

> __Every artifact should be reproduceable from the source code hash ([git reference](https://git-scm.com/book/en/v2/Git-Internals-Git-References))__


### Version bumping

Version is deduced from the current state of the SCM working copy: 

- is the source code on a tag? version is `1.5.2` 
- on a commit made after the tag? (possibly several commits) version is `1.5.2-f34b91` (use the commit hash in version number)
- with uncommitted change (DIRTY state)? `1.5.2-f34b91-DIRTY`

The version is never persisted somewhere in source code to avoid any desynchronisation between SCM state and version number. _However_, the library can optionaly spit the metadata (module name and version) in file to be included in an artefact during the build process.

** The SCM state drives the version. **

** Version uses the Semantic Versioning scheme. **

We never use SNAPSHOT in version number as it's difficult to know what's really inside the binary artefact.


### Module Naming

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
    
Module's name is by default deduced from the repo layout (but can also be overriden) and is given by Metav along the version.
In case of a dedicated repo for the module Metav takes the folder name containing the working copy (aka. containing the `.git` folder)

### Tagging behavior

Each _release_ invocation tags the current SCM state with the following naming scheme: `system-container-version`.
The tagging function use [git annotated tag](https://git-scm.com/book/en/v2/Git-Basics-Tagging) using the naming scheme describe previously, the message contains an EDN data structure described the module that is tagged: 

```clojure
{:module-name "Internet_banking-API" 
 :version "1.5.2"
 :path "Internet_Banking/API"
 :msg "Add new attachment feature in the message part of the system"}
``` 


### Meta management

Metadata, like module name and version, should be deduced from the SCM and include in the binary artefact (JAR, docker image) but never commited as file along the source code to avoid any desynchronisation. Metadata file is called `meta.edn`.

Metadata are:

- Module name
- version number
- timestamp
- Hash related to the version

See [spit function](#spit).

### Metav interface

#### Display Module name and Version

#### Release changes

#### Spit meta information (module name and version)



## Installation

Using tools.deps, add several alias in your `deps.edn` for each main task (display, spit, release) like this:
```
{:aliases {:display  {:extra-deps {jgrodziski/metav {:mvn/version "0.1.0"}}
                      :main-opts ["-m" "metav.display"]
}}}
```

## Usage

### Display current version

```
clj -A:metav -m metav.display
```

### Release

```
clj -A:metav -m metav.release -l minor
```

### Spit current versioning

```
clj -A:metav -m metav.spit
```


## License

Copyright © 2019 Jeremie Grodziski

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
