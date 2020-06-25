package scala.meta.annotations

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

/**
  * @author mutcianm
  * @since 26.03.17.
  */
class MetaAnnotationJarTest211 extends MetaAnnotationJarTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version  == LatestScalaVersions.Scala_2_11
}
