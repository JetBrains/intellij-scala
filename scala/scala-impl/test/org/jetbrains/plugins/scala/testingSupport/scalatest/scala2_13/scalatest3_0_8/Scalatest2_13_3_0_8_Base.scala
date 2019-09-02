package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_13.scalatest3_0_8

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_13}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

abstract class Scalatest2_13_3_0_8_Base extends ScalaTestTestCase {

  override implicit val version: ScalaVersion = Scala_2_13

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
    "org.scalatest" %% "scalatest" % "3.0.8",
    "org.scalactic" %% "scalactic" % "3.0.8"
  ) :: Nil
}
