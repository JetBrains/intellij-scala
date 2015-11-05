package org.jetbrains.plugins.scala
package testingSupport.specs2.specs2_2_10_2_4_6

import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
trait Specs2_2_10_2_4_6_Base extends Specs2TestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("specs2", "org.specs2/specs2_2.10/jars", "specs2_2.10-2.4.6.jar")
    addIvyCacheLibrary("scalaz-core", "org.scalaz/scalaz-core_2.10/bundles", "scalaz-core_2.10-7.1.0.jar")
    addIvyCacheLibrary("scalaz-concurrent", "org.scalaz/scalaz-concurrent_2.10/bundles", "scalaz-concurrent_2.10-7.1.0.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_10
}
