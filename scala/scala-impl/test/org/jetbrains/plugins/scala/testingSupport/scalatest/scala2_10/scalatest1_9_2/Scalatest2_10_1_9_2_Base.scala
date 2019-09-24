package org.jetbrains.plugins.scala
package testingSupport
package scalatest
package scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

abstract class Scalatest2_10_1_9_2_Base extends ScalaTestTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_10

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("org.scalatest" %% "scalatest" % "1.9.2") :: Nil
}
