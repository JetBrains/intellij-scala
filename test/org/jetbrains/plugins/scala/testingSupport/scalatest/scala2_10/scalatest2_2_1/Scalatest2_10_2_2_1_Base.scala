package org.jetbrains.plugins.scala
package testingSupport.scalatest.scala2_10.scalatest2_2_1

import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
abstract class Scalatest2_10_2_2_1_Base extends ScalaTestTestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(ScalaTestLoader("2.2.1", IvyLibraryLoader.Bundles))
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
