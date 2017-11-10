package org.jetbrains.plugins.scala.testingSupport.utest.scala2_11.utest_0_4_3

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.{ThirdPartyLibraryLoader, UTestLoader}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_11_0_4_3 extends UTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    Seq(UTestLoader("0.4.3"))
  }

  override protected val testSuiteSecondPrefix = ""
}
