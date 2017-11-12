package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_11_2_4_15

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
 * @author Roman.Shein
 * @since 11.01.2015.
 */
trait Specs2_2_11_2_4_15_Base extends Specs2TestCase {

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    Seq(Specs2Loader("2.4.15"), ScalaZCoreLoader(), ScalaZConcurrentLoader(), ScalaXmlLoader())
  }
}
