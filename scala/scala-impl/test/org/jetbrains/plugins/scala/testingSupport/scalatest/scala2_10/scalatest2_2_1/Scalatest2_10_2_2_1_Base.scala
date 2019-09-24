package org.jetbrains.plugins.scala
package testingSupport
package scalatest
package scala2_10.scalatest2_2_1

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

abstract class Scalatest2_10_2_2_1_Base extends ScalaTestTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_10

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("org.scalatest" %% "scalatest" % "2.2.1") :: Nil

}
