package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_11.scalatest3_0_1

import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
 * @author Roman.Shein
 * @since 10.03.2017
 */
abstract class Scalatest2_11_3_0_1_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(ScalaTestLoader("3.0.1", IvyLibraryLoader.Bundles), ScalaXmlLoader(), ScalacticLoader("3.0.1", IvyLibraryLoader.Bundles))
  }
}
