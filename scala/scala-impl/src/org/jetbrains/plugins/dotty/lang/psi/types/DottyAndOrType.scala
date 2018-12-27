package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeVisitor, ValueType, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
trait DottyAndOrType extends DottyType with ValueType {
  implicit override def projectContext: ProjectContext = left.projectContext

  val left: ScType
  val right: ScType
}

case class DottyAndType(override val left: ScType, override val right: ScType) extends DottyAndOrType {
  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitAndType(this)
    case _ =>
  }

  def updateSubtypes(substitutor: ScSubstitutor, variance: Variance)
                    (implicit visited: Set[ScType]): ScType = {
    DottyAndType(
      left.recursiveUpdateImpl(substitutor, variance),
      right.recursiveUpdateImpl(substitutor, variance)
    )
  }
}

object DottyAndType {
  def apply: Seq[ScType] => ScType = _.reduce(DottyAndType(_, _))
}

case class DottyOrType(override val left: ScType, override val right: ScType) extends DottyAndOrType {
  override def visitType(visitor: TypeVisitor): Unit = visitor match {
    case dottyVisitor: DottyTypeVisitor => dottyVisitor.visitOrType(this)
    case _ =>
  }

  def updateSubtypes(substitutor: ScSubstitutor, variance: Variance)
                    (implicit visited: Set[ScType]): ScType = {
    DottyOrType(
      left.recursiveUpdateImpl(substitutor, variance),
      right.recursiveUpdateImpl(substitutor, variance)
    )
  }
}

object DottyOrType {
  def apply: Seq[ScType] => ScType = _.reduce(DottyOrType(_, _))
}