package ru.kode.way.gradle

import com.android.build.api.variant.ComponentIdentity
import java.io.File

/**
 * Resolves the ordered list of Android source set names that contribute to [identity], from
 * lowest to highest priority — mirroring AGP's own precedence for merging per-source-set content
 * (resources, manifests, etc): `main` < product flavor(s) < build type < the variant-specific
 * (flavor+buildType combined) source set.
 *
 * [ComponentIdentity.getProductFlavors] is ordered by `flavorDimensions` declaration order, and per
 * AGP's own documented precedence the FIRST-declared dimension has the HIGHEST priority among
 * flavors ("Gradle determines the priority between flavor dimensions based on the order in which
 * they appear next to the flavorDimensions property, with the first dimension having a higher
 * priority than the second, and so on." —
 * https://developer.android.com/build/build-variants#flavor-dimensions). Since this function
 * builds an ASCENDING-priority list (consumed by [resolveOverriddenDotFiles], which lets later
 * entries win), the flavor list must be walked in REVERSE declaration order so the first-declared
 * dimension's flavor ends up last among the flavor entries — i.e. highest-priority.
 *
 * The variant-specific name is only included when it differs from the build type name — for a
 * project with no product flavors, [ComponentIdentity.getFlavorName] is empty and the variant
 * name equals the build type name (e.g. "debug"), so there is no distinct fourth source set.
 */
internal fun variantSourceSetNamesInPriorityOrder(identity: ComponentIdentity): List<String> {
  val names = mutableListOf("main")
  identity.productFlavors.asReversed().forEach { (_, flavorName) -> names.add(flavorName) }
  identity.buildType?.let { names.add(it) }
  if (identity.productFlavors.isNotEmpty() && identity.name != identity.buildType) {
    names.add(identity.name)
  }
  return names.distinct()
}

/**
 * File-level full-replacement override resolver.
 *
 * [sourceSetDirsInPriorityOrder] holds, for each contributing source set in ascending priority
 * order (as produced by [variantSourceSetNamesInPriorityOrder]), the list of `way/` directories
 * belonging to that source set. Every `.dot` file found under those directories is keyed by its
 * path relative to the containing directory; when a higher-priority source set has a `.dot` file
 * at the same relative path as a lower-priority one, it fully replaces it. Files present in only
 * one source set pass through unchanged.
 */
internal fun resolveOverriddenDotFiles(sourceSetDirsInPriorityOrder: List<List<File>>): List<File> {
  val fileByRelativePath = LinkedHashMap<String, File>()
  sourceSetDirsInPriorityOrder.forEach { dirs ->
    dirs.forEach { dir ->
      if (dir.isDirectory) {
        dir.walkTopDown()
          .filter { candidate -> candidate.isFile && candidate.extension == "dot" }
          .forEach { dotFile ->
            val relativePath = dotFile.relativeTo(dir).path
            fileByRelativePath[relativePath] = dotFile
          }
      }
    }
  }
  return fileByRelativePath.values.toList()
}
