# deps-v

Drive clojure project version built with [tools.deps](https://github.com/clojure/tools.deps.alpha) from git instead of the other way around.
Manage project's version using [Semantic Versioning](https://semver.org) and fits into a release process (like the leiningen ones). The `release` process should verify if anything is left uncommitted, bump the version number according to the change semantic given by the developer (minor, major or patch), then tag and down the version, push to the SCM repository


## Installation

Using tools.deps, add several alias in your `deps.edn` for each main task (display, spit, release) like this:
```
{:aliases {:display  {:extra-deps {jgrodziski/deps-v {:mvn/version "0.1.0"}}
                      :main-opts ["-m" "deps-v.display"]
}}}
```

## Usage

### Display current version

```
clj -A:deps-v -m deps-v.display
```

### Spit current versioning


### Release

### 

## License

Copyright © 2019 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
