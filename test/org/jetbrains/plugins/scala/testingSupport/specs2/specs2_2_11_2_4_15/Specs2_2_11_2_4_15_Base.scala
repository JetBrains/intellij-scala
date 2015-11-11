package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_11_2_4_15

import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 11.01.2015.
 */
trait Specs2_2_11_2_4_15_Base extends Specs2TestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("specs2", "org.specs2/specs2_2.11/jars", "specs2_2.11-2.4.15.jar")
    addIvyCacheLibrary("scalaz-core", "org.scalaz/scalaz-core_2.11/bundles", "scalaz-core_2.11-7.1.0.jar")
    addIvyCacheLibrary("scalaz-concurrent", "org.scalaz/scalaz-concurrent_2.11/bundles", "scalaz-concurrent_2.11-7.1.0.jar")
    addIvyCacheLibrary("scala-xml", "org.scala-lang.modules/scala-xml_2.11/bundles", "scala-xml_2.11-1.0.1.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_11

}
