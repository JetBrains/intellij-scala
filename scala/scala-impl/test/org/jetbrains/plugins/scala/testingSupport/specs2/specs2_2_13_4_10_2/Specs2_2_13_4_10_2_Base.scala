package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_13_4_10_2

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
  * @author Roman.Shein
  * @since 11.01.2015.
  */
trait Specs2_2_13_4_10_2_Base extends Specs2TestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13

  private val specsVersion: String = "4.10.2"

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    ("org.specs2" %% "specs2-core" % specsVersion).transitive()
  ) :: Nil
}