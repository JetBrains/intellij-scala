package scala.meta.annotations

import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}

/**
  * @author mutcianm
  * @since 26.03.17.
  */
class MetaAnnotationJarTest211 extends MetaAnnotationJarTest {
  override implicit val version: ScalaVersion = Scala_2_11
}
