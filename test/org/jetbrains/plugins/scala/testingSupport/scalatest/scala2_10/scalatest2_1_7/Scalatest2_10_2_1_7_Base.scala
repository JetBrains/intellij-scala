package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest2_1_7

import org.jetbrains.plugins.scala.base.libraryLoaders.{ScalaTestLoader, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Scalatest2_10_2_1_7_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(ScalaTestLoader("2.1.7"))
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
