package org.jetbrains.plugins.scala
package testingSupport.scalatest.scala2_10.scalatest2_2_1

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
trait Scalatest2_2_1_Base extends ScalaTestTestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addLibrary("scalaTest", "scalaTest", "scalatest_2.10-2.2.1.jar")
  }

  override protected val compilerDirectorySuffix: String = "2.10"
}
