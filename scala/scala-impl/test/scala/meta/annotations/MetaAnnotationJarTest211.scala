package scala.meta.annotations

import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_11}

/**
  * @author mutcianm
  * @since 26.03.17.
  */
class MetaAnnotationJarTest211 extends MetaAnnotationJarTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_11
}
