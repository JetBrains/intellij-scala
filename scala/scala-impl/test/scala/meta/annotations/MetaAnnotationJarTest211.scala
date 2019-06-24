package scala.meta.annotations

import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_11}

/**
  * @author mutcianm
  * @since 26.03.17.
  */
class MetaAnnotationJarTest211 extends {
  override implicit val version: ScalaVersion = Scala_2_11
} with MetaAnnotationJarTest
