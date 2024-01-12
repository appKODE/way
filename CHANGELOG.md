# Changelog

## 0.8.11 - TBD

* Remove explicit Schema parameter from NavigationService. It is now a public property of `NodeBuilder`

## 0.8.10 - 2023-11-01

* Fix issue with incorrect target resolution. Make code generator to generate segments having have a unique id (internally)

## 0.8.9 - 2023-10-16

* Fix issue with incorrect target resolution. Make code generator to generate segments having have a unique id (internally)

## 0.8.8 - 2023-10-05

* Add `Path.endsWith(other: Path)`
* Rename `onFinish` → `onFinishRequest`
* Fix issue which caused publishing jar to include code generated for tests

## 0.8.7 - 2023-09-12

* Update to kotlin 1.9.10 
* Update `way-compose` to Compose 1.5.1

## 0.8.6 - 2023-08-10

* Generated NodeBuilders now have an improved caching mechanism which is coupled with the correct cache invalidation: no more create-once-use-forever child nodes
* Fixed few bugs with Stay transition

## 0.8.5 - 2023-08-01

* Generated node builder factories now have an argument corresponding to the flow parameter (if any)

## 0.8.4 - 2023-06-28

* Improve NodeHost: optional starting, make utility functions public. This will aid in cases where one wishes to build their own NodeHost

## 0.8.3 - 2023-05-03

* Add support for kotlin-jvm projects to gradle plugin
* Rename `NodeFactory.createFlowNode()` to `NodeFactory.createRootNode()`

## 0.8.2 - 2023-03-07

* Add support for extending nodes through composition: provide `NodeExtensions` mechanism
* Add basic support for animated transitions in `NodeHost` for `way-compose` 
* Implement node hooks as one of `NodeExtension`s. Use `BaseFlowNode`, `BaseScreenNode` classes to take advantage of node hooks 
* Remove excessive logging in `way-gradle-plugin`

## 0.8.1 - 2023-03-01

* Fix issues with running `testDebugUnitTest` in consuming projects. Source sets were incorrectly set up by `way-gradle-plugin` for android projects.


## 0.8.0 - 2023-02-28

* Initial release

