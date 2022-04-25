package org.jetbrains.plugins.scala
package testingSupport
package scalatest
package scala2_11
package scalatest3_0_1

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

abstract class Scalatest2_11_3_0_1_Base extends ScalaTestTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version  == LatestScalaVersions.Scala_2_11

  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6",
      "org.scalatest" %% "scalatest" % "3.0.1",
      "org.scalactic" %% "scalactic" % "3.0.1"
    ) :: Nil

}
