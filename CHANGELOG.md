# Changelog

## 0.8.2 - 2023-03-07

* Add support for extending nodes through composition: provide `NodeExtensions` mechanism
* Add basic support for animated transitions in `NodeHost` for `way-compose` 
* Implement node hooks as one of `NodeExtension`s. Use `BaseFlowNode`, `BaseScreenNode` classes to take advantage of node hooks 
* Remove excessive logging in `way-gradle-plugin`

## 0.8.1 - 2023-03-01

* Fix issues with running `testDebugUnitTest` in consuming projects. Source sets were incorrectly set up by `way-gradle-plugin` for android projects.


## 0.8.0 - 2023-02-28

* Initial release

