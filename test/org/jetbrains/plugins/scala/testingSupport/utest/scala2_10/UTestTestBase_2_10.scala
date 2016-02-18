package org.jetbrains.plugins.scala.testingSupport.utest.scala2_10

import org.jetbrains.plugins.scala.testingSupport.utest.{UTestSimpleTest, UTestTestCase}
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_10 extends UTestTestCase {

  /**
    * Intended for loading libraries different from scala-compiler.
    */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("qQuotes", "org.scalamacros/quasiquotes_2.10/jars", "quasiquotes_2.10-2.0.0.jar")
    addIvyCacheLibrary("utest", "com.lihaoyi/utest_2.10/jars", "utest_2.10-0.3.1.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
