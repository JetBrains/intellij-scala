package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeVisitor, ValueType}

/**
  * @author adkozlov
  */
trait DottyAndOrType extends DottyType with ValueType {
  val left: ScType
  val right: ScType
}

case class DottyAndType(override val left: ScType, override val right: ScType) extends DottyAndOrType {
  override def visitType(visitor: TypeVisitor) = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitAndType(this)
    case _ =>
  }
}

object DottyAndType {
  def apply: Seq[ScType] => ScType = _.reduce(DottyAndType(_, _))
}

case class DottyOrType(override val left: ScType, override val right: ScType) extends DottyAndOrType {
  override def visitType(visitor: TypeVisitor) = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitOrType(this)
    case _ =>
  }
}

object DottyOrType {
  def apply: Seq[ScType] => ScType = _.reduce(DottyOrType(_, _))
}