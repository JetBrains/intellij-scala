package org.jetbrains.plugins.scala.base.libraryLoaders

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

object ScalaStandardLibraryLoaders {

  /**
   * Since Scala 2.11 `scala.xml` is no longer part of `scala-library` and is published as a
   * separate `scala-xml` module. For Scala 2.10 and earlier it is still bundled into the standard
   * library, so no extra loader is required there.
   *
   * `scala-xml` `1.3.0` is cross-published for 2.11, 2.12 and 2.13.
   */
  def scalaXmlLoaders(implicit version: ScalaVersion): Seq[LibraryLoader] =
    if (version >= LatestScalaVersions.Scala_2_11)
      Seq(IvyManagedLoader("org.scala-lang.modules" %% "scala-xml" % "1.3.0"))
    else
      Nil
}
