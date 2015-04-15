package org.jetbrains.sbt.project.modifier

/**
 * Marker class used to distinguish between requests when searching for a modification location using
 * BuildFileModificationLocationProvider.
 * @author Roman.Shein
 * @since 16.03.2015.
 */
case class BuildFileElementType(id: String)

object BuildFileElementType {
  val libraryDependencyElementId = BuildFileElementType("LIBRARY_DEPENDENCY")
  val resolverElementId = BuildFileElementType("RESOLVER")
  val scalacOptionsElementId = BuildFileElementType("SCALAC_OPTIONS")
}
