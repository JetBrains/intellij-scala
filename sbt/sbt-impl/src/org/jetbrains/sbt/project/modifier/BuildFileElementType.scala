package org.jetbrains.sbt.project.modifier

import org.jetbrains.annotations.NonNls

/**
 * Marker class used to distinguish between requests when searching for a modification location using
 * BuildFileModificationLocationProvider.
 */
case class BuildFileElementType(@NonNls id: String)

object BuildFileElementType {
  val libraryDependencyElementId: BuildFileElementType = BuildFileElementType("LIBRARY_DEPENDENCY")
  val resolverElementId: BuildFileElementType = BuildFileElementType("RESOLVER")
  val scalacOptionsElementId: BuildFileElementType = BuildFileElementType("SCALAC_OPTIONS")
}
