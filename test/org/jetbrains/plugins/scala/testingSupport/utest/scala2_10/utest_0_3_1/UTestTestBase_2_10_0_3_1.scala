package org.jetbrains.plugins.scala.testingSupport.utest.scala2_10.utest_0_3_1

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.{QuasiQuotesLoader, ThirdPartyLibraryLoader, UTestLoader}
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_10}
import org.jetbrains.plugins.scala.testingSupport.utest.UTestTestCase

/**
  * @author Roman.Shein
  * @since 02.09.2015.
  */
abstract class UTestTestBase_2_10_0_3_1 extends UTestTestCase {

  override implicit val version: ScalaVersion = Scala_2_10

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module: Module = getModule
    Seq(UTestLoader("0.3.1"), QuasiQuotesLoader())
  }
}
