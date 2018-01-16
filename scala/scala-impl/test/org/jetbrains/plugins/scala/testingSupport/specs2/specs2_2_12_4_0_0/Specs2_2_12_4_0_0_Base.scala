package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_12_4_0_0

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
  * @author Roman.Shein
  * @since 11.01.2015.
  */
trait Specs2_2_12_4_0_0_Base extends Specs2TestCase {

  override implicit val version: ScalaVersion = Scala_2_12

  private val specsVersion: String = "4.0.0"

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
      "org.specs2" %% "specs2-core" % specsVersion,
      "org.specs2" %% "specs2-common" % specsVersion,
      "org.specs2" %% "specs2-matcher" % specsVersion,
      "org.specs2" %% "specs2-fp" % specsVersion,
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
  ) :: Nil
}