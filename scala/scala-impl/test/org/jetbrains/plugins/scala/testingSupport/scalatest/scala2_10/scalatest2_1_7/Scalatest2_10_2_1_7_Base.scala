package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest2_1_7

import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_10}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.junit.experimental.categories.Category

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
@Category(Array(classOf[SlowTests]))
abstract class Scalatest2_10_2_1_7_Base extends ScalaTestTestCase {

  override implicit val version: ScalaVersion = Scala_2_10

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("org.scalatest" %% "scalatest" % "2.1.7") :: Nil

}
