package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 11.02.2015.
 */
abstract class Scalatest2_10_1_9_2_Base extends ScalaTestTestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("scalaTest", "org.scalatest/scalatest_2.10/jars", "scalatest_2.10-1.9.2.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
