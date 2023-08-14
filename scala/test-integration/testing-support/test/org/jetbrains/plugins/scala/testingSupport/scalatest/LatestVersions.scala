package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestApiSymbols
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

trait WithScalaTest_2_2 extends ScalaSdkOwner {
  abstract override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders ++ Seq(
    IvyManagedLoader(("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_2_2).transitive())
  )
}

trait WithScalaTest_3_0 extends ScalaSdkOwner {
  abstract override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders ++ Seq(
    IvyManagedLoader(("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_3_0).transitive())
  )
}

trait WithScalaTest_3_1 extends ScalaSdkOwner {
  abstract override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders ++ Seq(
    IvyManagedLoader(("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_3_1).transitive())
  )
}

trait WithScalaTest_3_2 extends ScalaSdkOwner with ScalaTestApiSymbols.SinceScalatest_3_2 {
  abstract override protected def librariesLoaders: Seq[LibraryLoader] = super.librariesLoaders ++ Seq(
    IvyManagedLoader(("org.scalatest" %% "scalatest" % ScalaTestLatestVersions.Scalatest_3_2).transitive())
  )
}

trait WithScala_2_11 extends ScalaSdkOwner {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}
trait WithScala_2_12 extends ScalaSdkOwner {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}
trait WithScala_2_13 extends ScalaSdkOwner {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}
trait WithScala_3 extends ScalaSdkOwner {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3
}