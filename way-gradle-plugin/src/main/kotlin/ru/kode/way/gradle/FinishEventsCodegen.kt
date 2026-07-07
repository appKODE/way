package ru.kode.way.gradle

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT

internal fun buildChildFinishEventFileSpecs(
  parseResult: SchemaParseResult,
  config: CodeGenConfig,
  @Suppress("UNUSED_PARAMETER") registry: SchemaRegistry,
  emitParentParallelEntries: Boolean = true,
): List<FileSpec> {
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val adjacencyList = parseResult.adjacencyList
  val regionRoots = buildRegionRoots(adjacencyList)

  // Collect (parentInterfaceId -> ordered list of child flow nodes) for each interface to generate.
  // Two sources contribute:
  // 1. For each regionRoot R that is a flow child of a LocalParallel P: R contributes to PChildFinishRequest.
  //    Skipped when [emitParentParallelEntries] is false (e.g. when generating finish-event specs for a
  //    virtual sub-schema, where the outer schema is responsible for emitting the parent's entries).
  // 2. For each regionRoot R: flows discovered by DFS from R contribute to RChildFinishRequest.
  val interfaceGroups = mutableMapOf<String, MutableList<Node.Flow>>()

  regionRoots.forEach { regionRoot ->
    if (emitParentParallelEntries) {
      val parallelParent = adjacencyList.parallelParentOf(regionRoot)
      if (parallelParent != null && regionRoot is Node.Flow) {
        interfaceGroups.getOrPut(parallelParent.id) { mutableListOf() }.add(regionRoot)
      }
    }
    // A regionRoot that is a LOCAL flow child of a LocalParallel owns its own virtual sub-schema
    // — that sub-schema is responsible for emitting `<regionRoot>ChildFinishRequest`. Skip the
    // outer DFS to avoid generating a duplicate (and colliding) interface here.
    if (adjacencyList.isLocalChildOfParallel(regionRoot)) {
      return@forEach
    }
    dfsWhile(adjacencyList, regionRoot) { node ->
      if (node != regionRoot && node is Node.Flow) {
        interfaceGroups.getOrPut(regionRoot.id) { mutableListOf() }.add(node)
      }
      // Stop descent at any node that has its own virtual sub-schema — that sub-schema emits
      // its own child interfaces. Without this prune we would attribute deeply-nested flows to
      // the wrong parent (their grandparent's interface).
      val ownsVirtualSubSchema = node is Node.Flow.LocalParallel || adjacencyList.isLocalChildOfParallel(node)
      node === regionRoot || !ownsVirtualSubSchema
    }
  }

  return interfaceGroups
    .filterValues { it.isNotEmpty() }
    .map { (parentId, childFlows) ->
      buildFinishEventFileSpec(packageName, parentId, childFlows.distinctBy { it.id })
    }
}

private fun buildFinishEventFileSpec(
  packageName: String,
  parentId: String,
  childFlowNodes: List<Node.Flow>,
): FileSpec {
  val className = ClassName(packageName, childFinishRequestInterfaceName(parentId))
  return FileSpec
    .builder(packageName, className.simpleName)
    .addType(
      TypeSpec.interfaceBuilder(className)
        .addModifiers(KModifier.SEALED)
        .addSuperinterface(EVENT)
        .apply {
          childFlowNodes.forEach { node ->
            val resultClassName = parseTypeName(node.resultType)
            if (resultClassName != UNIT) {
              addType(
                TypeSpec.classBuilder(node.id.toPascalCase())
                  .addModifiers(KModifier.DATA)
                  .addSuperinterface(className)
                  .primaryConstructor(
                    FunSpec.constructorBuilder()
                      .addParameter("result", resultClassName)
                      .build(),
                  )
                  .addProperty(
                    PropertySpec.builder("result", resultClassName)
                      .initializer("result")
                      .build(),
                  )
                  .build(),
              )
            } else {
              addType(
                TypeSpec.objectBuilder(node.id.toPascalCase())
                  .addModifiers(KModifier.DATA)
                  .addSuperinterface(className)
                  .build(),
              )
            }
          }
        }
        .build(),
    )
    .build()
}

internal fun childFinishRequestInterfaceName(nodeId: String) = nodeId.toPascalCase() + "ChildFinishRequest"
internal fun childFinishRequestEventClassName(packageName: String, flowNodeId: String, childFlowNodeId: String) =
  ClassName(packageName, childFinishRequestInterfaceName(flowNodeId)).nestedClass(childFlowNodeId.toPascalCase())
