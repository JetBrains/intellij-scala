package scala.meta.annotations

import org.jetbrains.plugins.scala.debugger.{ScalaSdkOwner, ScalaVersion, Scala_2_12}

/**
  * @author mutcianm
  * @since 26.03.17.
  */
class MetaAnnotationJarTest212 extends MetaAnnotationJarTest {
  override implicit val version = Scala_2_12
  override protected val jarPath = "/addFoo_2.12_3.0.0-M7.jar"
}
