package org.jetbrains.plugins.scala.testingSupport.specs2

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader, ScalaReflectLibraryLoader}

/**
 * Mixin for `ScalaSdkOwner`-based test fixtures (e.g. `GutterMarkersTestBase`)
 * that need specs2 jars on the classpath. Contributes loaders only — no
 * other behaviour. Pinned to the same version as
 * [[org.jetbrains.plugins.scala.testingSupport.specs2.specs2_scala_2_13_specs_4.Specs2_Scala_2_13_Specs_4_Base]].
 */
trait WithSpecs2_4 extends ScalaSdkOwner {
  abstract override protected def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders ++ Seq(
      ScalaReflectLibraryLoader, // specs library depends on scala-reflect, do not ignore it
      IvyManagedLoader(("org.specs2" %% "specs2-core" % "4.13.0").transitive())
    )
}
