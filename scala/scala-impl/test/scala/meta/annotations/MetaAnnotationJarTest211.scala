package scala.meta.annotations

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.junit.experimental.categories.Category

/**
  * @author mutcianm
  * @since 26.03.17.
  */
@Category(Array(classOf[SlowTests]))
class MetaAnnotationJarTest211 extends {
  override implicit val version: ScalaVersion = Scala_2_11
} with MetaAnnotationJarTest
