package org.jetbrains.plugins.scala.testingSupport.utest.scala2_12.utest_0_5_4

import org.jetbrains.plugins.scala.base.libraryLoaders.{ThirdPartyLibraryLoader, UTestLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_12_0_5_4 extends UTestTestCase {

  override implicit val version: ScalaVersion = Scala_2_12

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(UTestLoader("0.5.4"))
  }

  override protected val testSuiteSecondPrefix = ""
}
