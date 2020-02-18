# Change Log
All notable changes to this project will be documented in this file. 
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

# [1.6.2] - 2020-02-18

Add explicit behavior for spit
See (Issue 14)[https://github.com/jgrodziski/metav/issues/14]

# [1.6.1] - 2019-10-28

Refactor to provide a clean api to facilitate using metav within code and REPL, spec for options of operations.
Add some documentation (`docs/doc.md`)
See (Pull Request 9)[https://github.com/jgrodziski/metav/pull/9]

# [1.5.4] - 2019-09-20

Bumping edge case when bumping a dirty version (see https://github.com/jgrodziski/metav/pull/7)

# [1.5.3]
Fix Issue : https://github.com/jgrodziski/metav/issues/6
Fix sanity check of the repo state before release when language is different than english

# [1.5.2]
Fix Issue: https://github.com/jgrodziski/metav/issues/4
Take the first line of a multi line where.exe output to find git

[1.5.0, 1.5.1]
Adding option to skip signing git tag
Patch for doc of the previous feature in 1.5.1

# [1.4.0]
Add a template rendering option for spit and release task using the mustache templating format EADe )

# [1.3.0]
Add an option to avoid pushing at the end of the release task 

# [1.2.0] - 2019-02-27 #

Add the module-name override CLI options to overide the module-name instead of the automatic deduction given the directory tree.

## 1.0.0 - 2019-01-28
### Added
- Release Task (see metav.release ns)
- Display Task (see metav.display ns) 

[Unreleased]: https://github.com/jgrodziski/metav/compare/0.1.1...HEAD
[0.1.1]: https://github.com/jgrodziski/metav/compare/0.1.0...0.1.1
