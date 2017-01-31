package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_10.scalatest1_9_2

import org.jetbrains.plugins.scala.base.libraryLoaders.{ScalaTestLoader, ThirdPartyLibraryLoader}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Roman.Shein
  * @since 11.02.2015.
  */
abstract class Scalatest2_10_1_9_2_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(ScalaTestLoader("1.9.2"))
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
