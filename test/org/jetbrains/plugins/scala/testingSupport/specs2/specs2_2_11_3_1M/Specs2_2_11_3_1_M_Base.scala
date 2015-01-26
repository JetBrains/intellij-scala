package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_11_3_1M

import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase

/**
 * @author Roman.Shein
 * @since 11.01.2015.
 */
trait Specs2_2_11_3_1_M_Base extends Specs2TestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addLibrary("specs2", "specs2", "specs2-common_2.11-3.0-M1.jar", "specs2-core_2.11-3.0-M1.jar", "specs2-matcher_2.11-3.0-M1.jar")
    addLibrary("scalaz", "scalaz", "scalaz-core_2.11-7.1.0.jar", "scalaz-concurrent_2.11-7.1.0.jar", "scalaz-effect_2.11-7.1.0.jar", "scalaz-stream_2.11-0.6a.jar")
    addLibrary("scala-xml", "scala-xml", "scala-xml_2.11-1.0.1.jar")
    addLibrary("shapeless", "shapeless", "shapeless_2.11-2.0.0.jar")
    addLibrary("scodec", "scodec", "scodec-bits_2.11-1.1.0-SNAPSHOT.jar", "scodec-core_2.11-1.7.0-SNAPSHOT.jar")
  }

  override protected val compilerDirectorySuffix: String = "2.11"

}
