package org.jetbrains.plugins.scala
package testingSupport.specs2.specs2_2_10_2_4_6

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_10}
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
trait Specs2_2_10_2_4_6_Base extends Specs2TestCase {

  override implicit val version: ScalaVersion = Scala_2_10

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    Seq(Specs2Loader("2.4.6"), ScalaZCoreLoader(), ScalaZConcurrentLoader())
  }
}
