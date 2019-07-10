package org.jetbrains.plugins.scala
package testingSupport
package utest
package scala2_11
package utest_0_4_3

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

abstract class UTestTestBase_2_11_0_4_3 extends UTestTestCase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_11

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("com.lihaoyi" %% "utest" % "0.4.3") :: Nil

  override protected val testSuiteSecondPrefix = ""
}
