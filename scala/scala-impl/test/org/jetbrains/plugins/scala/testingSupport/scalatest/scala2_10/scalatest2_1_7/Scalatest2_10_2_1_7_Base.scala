package org.jetbrains.plugins.scala
package testingSupport
package scalatest
package scala2_10.scalatest2_1_7

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Scalatest2_10_2_1_7_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("org.scalatest" %% "scalatest" % "2.1.7") :: Nil

}
