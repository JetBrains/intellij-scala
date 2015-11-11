package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest2_2_1

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 22.01.2015
 */
abstract class Scalatest2_11_2_2_1_Base extends ScalaTestTestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("scalaTest", "org.scalatest/scalatest_2.11/bundles", "scalatest_2.11-2.2.1.jar")
    addIvyCacheLibrary("scala-xml", "org.scala-lang.modules/scala-xml_2.11/bundles", "scala-xml_2.11-1.0.1.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_11
}
