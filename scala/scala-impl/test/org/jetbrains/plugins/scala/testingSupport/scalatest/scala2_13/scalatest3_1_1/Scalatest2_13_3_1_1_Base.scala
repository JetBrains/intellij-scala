package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_13.scalatest3_1_1

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.{FeatureSpecApi, ScalaTestTestCase}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class Scalatest2_13_3_1_1_Base extends ScalaTestTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
    "org.scalatest" %% "scalatest" % "3.1.1",
    "org.scalactic" %% "scalactic" % "3.1.1"
  ) :: Nil
}
