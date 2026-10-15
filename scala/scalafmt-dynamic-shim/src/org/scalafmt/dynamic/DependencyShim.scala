package org.scalafmt.dynamic

import org.scalafmt.interfaces.RepositoryPackage

/**
 * Exposes [[Dependency]], which is `private[dynamic]` since scalafmt 3.11.0.
 *
 * This object is in the same package, so it links against [[Dependency]] directly.
 * If its API changes when we update `scalafmt-dynamic-core`, the compilation fails instead of the scalafmt download.
 */
object DependencyShim {

  /** @return the packages to download (scalafmt-core and, for Scala 2 versions of it, scala-reflect) for the given scalafmt version */
  def dependencies(version: ScalafmtVersion): Seq[RepositoryPackage] =
    Dependency.dependencies(version)._2
}
