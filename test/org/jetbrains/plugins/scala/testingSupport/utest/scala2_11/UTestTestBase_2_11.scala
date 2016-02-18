package org.jetbrains.plugins.scala.testingSupport.utest.scala2_11

import org.jetbrains.plugins.scala.testingSupport.utest.{UTestSimpleTest, UTestTestCase}
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_11 extends UTestTestCase {

  /**
    * Intended for loading libraries different from scala-compiler.
    */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("utest", "com.lihaoyi/utest_2.11/jars", "utest_2.11-0.3.1.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_11
}
