package org.jetbrains.plugins.scala
package testingSupport.specs2.specs2_2_10_2_4_6

import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
trait Specs2_2_10_2_4_6_Base extends Specs2TestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module = getModule
    Seq(Specs2Loader("2.4.6"), ScalaZCoreLoader(), ScalaZConcurrentLoader())
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
