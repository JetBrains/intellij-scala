package org.jetbrains.plugins.scala.testingSupport.specs2.specs2_2_11_3_1M

import org.jetbrains.plugins.scala.testingSupport.specs2.Specs2TestCase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
 * @author Roman.Shein
 * @since 11.01.2015.
 */
trait Specs2_2_11_3_1_M_Base extends Specs2TestCase {
  /**
   * Intended for loading libraries different from scala-compiler.
   */
  override protected def addOtherLibraries(): Unit = {
    addIvyCacheLibrary("specs2-common", "org.specs2/specs2-common_2.11/jars", "specs2-common_2.11-3.0.1.jar")
    addIvyCacheLibrary("specs2-core", "org.specs2/specs2-core_2.11/jars", "specs2-core_2.11-3.0.1.jar")
    addIvyCacheLibrary("specs2-matcher", "org.specs2/specs2-matcher_2.11/jars", "specs2-matcher_2.11-3.0.1.jar")
    addIvyCacheLibrary("scalaz-core", "org.scalaz/scalaz-core_2.11/bundles", "scalaz-core_2.11-7.1.0.jar")
    addIvyCacheLibrary("scalaz-concurrent", "org.scalaz/scalaz-concurrent_2.11/bundles", "scalaz-concurrent_2.11-7.1.0.jar")
    addIvyCacheLibrary("scalaz-effect", "org.scalaz/scalaz-effect_2.11/bundles", "scalaz-effect_2.11-7.1.0.jar")
    addIvyCacheLibrary("scalaz-stream", "org.scalaz.stream/scalaz-stream_2.11/bundles", "scalaz-stream_2.11-0.6a.jar")
    addIvyCacheLibrary("scala-xml", "org.scala-lang.modules/scala-xml_2.11/bundles", "scala-xml_2.11-1.0.1.jar")
    addIvyCacheLibrary("shapeless", "com.chuusai/shapeless_2.11/bundles", "shapeless_2.11-2.0.0.jar")
    addIvyCacheLibrary("scodec-bits", "org.typelevel/scodec-bits_2.11/bundles", "scodec-bits_2.11-1.1.0-SNAPSHOT.jar")
    addIvyCacheLibrary("scodec-core", "org.typelevel/scodec-core_2.11/bundles", "scodec-core_2.11-1.7.0-SNAPSHOT.jar")
  }

  override protected val scalaSdkVersion = ScalaSdkVersion._2_11

}