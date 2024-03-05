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
  config: CodeGenConfig
): FileSpec? {
  val packageName = parseResult.customPackage ?: config.outputPackageName
  val rootNode = parseResult.adjacencyList.findRootNode()

  // all "local" flows are considered to be children of the root flow in the dot file
  // (remember! dot file graph specifies backstack, not structure!)
  // Therefore only one "ChildEvents"-class must be generated per dot-file.
  // Complex multiple flows in one dot file are not permitted, better to factor out logic in multiple dot files in
  // this case.
  // See NOTE_GROUPING_NODES_BY_FLOW_RULE

  val childFlowNodes = mutableListOf<Node.Flow>()
  dfs(parseResult.adjacencyList, rootNode) { node ->
    if (node == rootNode) return@dfs
    when (node) {
      is Node.Flow -> {
        childFlowNodes.add(node)
      }
      is Node.Screen -> Unit
    }
  }

  if (childFlowNodes.isEmpty()) {
    return null
  }

  val className = ClassName(packageName, childFinishRequestInterfaceName(rootNode.id))
  return FileSpec
    .builder(
      packageName,
      className.simpleName
    )
    .addType(
      TypeSpec.interfaceBuilder(className)
        .addModifiers(KModifier.SEALED)
        .addSuperinterface(EVENT)
        .apply {
          childFlowNodes.forEach { node ->
            val resultClassName = ClassName.bestGuess(node.resultType)
            if (resultClassName != UNIT) {
              addType(
                TypeSpec.classBuilder(node.id.toPascalCase())
                  .addModifiers(KModifier.DATA)
                  .addSuperinterface(className)
                  .primaryConstructor(
                    FunSpec.constructorBuilder()
                      .addParameter("result", resultClassName)
                      .build()
                  )
                  .addProperty(
                    PropertySpec.builder("result", resultClassName)
                      .initializer("result")
                      .build()
                  )
                  .build()
              )
            } else {
              addType(
                TypeSpec.objectBuilder(node.id.toPascalCase())
                  .addModifiers(KModifier.DATA)
                  .addSuperinterface(className)
                  .build()
              )
            }
          }
        }
        .build()
    )
    .build()
}

internal fun childFinishRequestInterfaceName(nodeId: String) = nodeId.toPascalCase() + "ChildFinishRequest"
internal fun childFinishRequestEventClassName(
  packageName: String,
  flowNodeId: String,
  childFlowNodeId: String
) = ClassName(packageName, childFinishRequestInterfaceName(flowNodeId) + '.' + childFlowNodeId.toPascalCase())
