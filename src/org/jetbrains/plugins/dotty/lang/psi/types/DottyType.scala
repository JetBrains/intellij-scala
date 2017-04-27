package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeVisitor, ValueType}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
trait DottyType extends ScType {
  override def typeSystem: DottyTypeSystem = DottyTypeSystem
}

// is value type?
class DottyNoType(implicit val projectContext: ProjectContext) extends DottyType with ValueType {
  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitNoType(this)
    case _ =>
  }

  override def isFinalType = true

  override def equals(other: Any): Boolean = other.isInstanceOf[DottyNoType]

  override def hashCode(): Int = DottyNoType.hashCode()
}

object DottyNoType {
  def apply()(implicit projectContext: ProjectContext) = new DottyNoType()
  def unapply(t: DottyNoType): Boolean = true
}

trait DottyConstantType extends DottyType