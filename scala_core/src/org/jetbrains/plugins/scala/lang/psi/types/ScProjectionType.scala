package org.jetbrains.plugins.scala.lang.psi.types

/**
* @author ilyas
*/

case class ScProjectionType(projected: ScType, name: String) extends ScType {
  override def equiv(t : ScType) = t match {
    case ScProjectionType(p1, n1) => n1 == name && (projected equiv p1)
    case _ => false
  }
}