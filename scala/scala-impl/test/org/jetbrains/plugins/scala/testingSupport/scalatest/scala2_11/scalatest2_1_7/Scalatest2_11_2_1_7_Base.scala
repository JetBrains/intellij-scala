package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest2_1_7

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders.{ScalaTestLoader, ScalaXmlLoader, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
 * @author Roman.Shein
 * @since 22.01.2015
 */
abstract class Scalatest2_11_2_1_7_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    Seq(ScalaTestLoader("2.1.7"), ScalaXmlLoader())
  }
}
