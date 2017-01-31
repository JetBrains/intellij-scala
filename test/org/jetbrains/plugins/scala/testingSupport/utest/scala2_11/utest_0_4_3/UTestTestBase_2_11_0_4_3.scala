package org.jetbrains.plugins.scala.testingSupport.utest.scala2_11.utest_0_4_3

import org.jetbrains.plugins.scala.base.libraryLoaders.{ThirdPartyLibraryLoader, UTestLoader}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_11_0_4_3 extends UTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(UTestLoader("0.4.3"))
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_11

  override protected val testSuiteSecondPrefix = ""
}
