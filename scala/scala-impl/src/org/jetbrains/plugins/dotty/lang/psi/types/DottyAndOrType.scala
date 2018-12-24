package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.{ScType, recursiveUpdate}
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

  def updateSubtypes(substitutor: ScSubstitutor, visited: Set[ScType]): ScType =
    DottyAndType(substitutor(left), substitutor(right))

  def updateSubtypesVariance(update: (ScType, Variance) => recursiveUpdate.AfterUpdate,
                             variance: Variance,
                             revertVariances: Boolean)
                            (implicit visited: Set[ScType]): ScType = {
    DottyAndType(
      left.recursiveVarianceUpdate(update, variance),
      right.recursiveVarianceUpdate(update, variance)
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

  def updateSubtypes(substitutor: ScSubstitutor, visited: Set[ScType]): ScType =
    DottyOrType(substitutor(left), substitutor(right))

  def updateSubtypesVariance(update: (ScType, Variance) => recursiveUpdate.AfterUpdate,
                             variance: Variance,
                             revertVariances: Boolean)
                            (implicit visited: Set[ScType]): ScType = {
    DottyOrType(
      left.recursiveVarianceUpdate(update, variance),
      right.recursiveVarianceUpdate(update, variance)
    )
  }
}

object DottyOrType {
  def apply: Seq[ScType] => ScType = _.reduce(DottyOrType(_, _))
}