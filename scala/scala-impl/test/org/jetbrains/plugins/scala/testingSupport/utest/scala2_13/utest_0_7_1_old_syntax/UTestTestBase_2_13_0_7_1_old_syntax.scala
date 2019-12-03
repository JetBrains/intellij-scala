package org.jetbrains.plugins.scala.testingSupport.utest.scala2_13.utest_0_7_1_old_syntax

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_13}
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

abstract class UTestTestBase_2_13_0_7_1 extends UTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_13

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("com.lihaoyi" %% "utest" % "0.7.1") :: Nil

  override protected val testSuiteSecondPrefix = ""
}
