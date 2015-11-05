package org.jetbrains.plugins.scala
package testingSupport.scalatest.scala2_10.scalatest2_2_1

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Scalatest2_10_2_2_1_Base extends ScalaTestTestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("scalaTest", "org.scalatest/scalatest_2.10/bundles", "scalatest_2.10-2.2.1.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
