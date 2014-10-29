package org.jetbrains.plugins.scala
package testingSupport.specs2.specs2_2_10_2_4_6

import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
 * @author Roman.Shein
 * @since 16.10.2014.
 */
trait Specs2_2_10_2_4_6_Base extends Specs2TestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addLibrary("specs2", "specs2", "specs2_2.10-2.4.6.jar")
    addLibrary("scalaz", "scalaz", "scalaz-core_2.10-7.1.0.jar", "scalaz-concurrent_2.10-7.1.0.jar")
  }

  override protected val compilerDirectorySuffix: String = "2.10"
}
