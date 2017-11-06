package org.jetbrains.plugins.scala.testingSupport.scalatest.scala2_12.scalatest3_0_4

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.base.libraryLoaders._
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
 * @author Roman.Shein
 * @since 10.03.2017
 */
abstract class Scalatest2_12_3_0_4_Base extends ScalaTestTestCase {

  override implicit val version: ScalaVersion = Scala_2_12

  override protected def additionalLibraries: Seq[ThirdPartyLibraryLoader] = {
    implicit val module: Module = getModule
    Seq(ScalaTestLoader("3.0.4", IvyLibraryLoader.Bundles), ScalaXmlLoader(), ScalacticLoader("3.0.4", IvyLibraryLoader.Bundles))
  }
}
