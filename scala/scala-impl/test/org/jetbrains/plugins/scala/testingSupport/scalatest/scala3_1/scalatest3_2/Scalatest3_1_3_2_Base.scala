package org.jetbrains.plugins.scala.testingSupport.scalatest.scala3_1.scalatest3_2

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.{ScalaTestApiSymbols, ScalaTestTestCase}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class Scalatest3_1_3_2_Base
  extends ScalaTestTestCase
    with ScalaTestApiSymbols.SinceScalatest32 {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3_1

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    ("org.scalatest" %% "scalatest" % "3.2.12").transitive(),
  ) :: Nil
}
