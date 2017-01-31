package org.jetbrains.plugins.scala.testingSupport.utest.scala2_10.utest_0_3_1

import org.jetbrains.plugins.scala.base.libraryLoaders.{QuasiQuotesLoader, ThirdPartyLibraryLoader, UTestLoader}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_10_0_3_1 extends UTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(UTestLoader("0.3.1"), QuasiQuotesLoader())
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
