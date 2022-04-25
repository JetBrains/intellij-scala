package org.jetbrains.plugins.scala
package testingSupport
package scalatest
package scala2_11
package scalatest2_2_1

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

abstract class Scalatest2_11_2_2_1_Base extends ScalaTestTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version  == LatestScalaVersions.Scala_2_11

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(
      "org.scalatest" %% "scalatest" % "2.2.1",
      "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
    ) :: Nil

}
