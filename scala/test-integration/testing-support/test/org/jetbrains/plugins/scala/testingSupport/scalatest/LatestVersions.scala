package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.{ScalaTestApiSymbols, ScalaTestTestCase}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
 * @see [[https://github.com/scalatest/scalatest/releases]]
 */
object ScalaTestLatestVersions {
  val Scalatest_2_2 = "2.2.6"
  val Scalatest_3_0 = "3.0.9"
  val Scalatest_3_1 = "3.1.4"
  val Scalatest_3_2 = "3.2.16"
}

trait WithScalaTest_2_2 extends ScalaTestTestCase {
  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader(
      ("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_2_2).transitive(),
    ) :: Nil
}

trait WithScalaTest_3_0 extends ScalaTestTestCase {
  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    ("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_3_0).transitive(),
  ) :: Nil
}

trait WithScalaTest_3_1 extends ScalaTestTestCase {
  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    ("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_3_1).transitive()
  ) :: Nil
}

trait WithScalaTest_3_2 extends ScalaTestTestCase with ScalaTestApiSymbols.SinceScalatest_3_2  {
  override protected def additionalLibraries: Seq[LibraryLoader] = IvyManagedLoader(
    ("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_3_2).transitive(),
  ) :: Nil
}

trait WithScala_2_11 extends ScalaTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}
trait WithScala_2_12 extends ScalaTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}
trait WithScala_2_13 extends ScalaTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}
trait WithScala_3 extends ScalaTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3
}