package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest2_1_7

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Scalatest2_10_2_1_7_Base extends ScalaTestTestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("scalaTest", "org.scalatest/scalatest_2.10/jars", "scalatest_2.10-2.1.7.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
