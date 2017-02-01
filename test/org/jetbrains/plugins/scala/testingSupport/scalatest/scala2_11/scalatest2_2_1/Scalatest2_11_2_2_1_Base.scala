package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest2_2_1

import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 22.01.2015
 */
abstract class Scalatest2_11_2_2_1_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(ScalaTestLoader("2.2.1", IvyLibraryLoader.Bundles), ScalaXmlLoader())
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_11
}
