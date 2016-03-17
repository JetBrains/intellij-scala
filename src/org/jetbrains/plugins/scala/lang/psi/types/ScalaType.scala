package org.jetbrains.plugins.scala.lang.psi.types

/**
  * @author adkozlov
  */
trait ScalaType extends ScType {
  implicit val typeSystem = ScalaTypeSystem
}
