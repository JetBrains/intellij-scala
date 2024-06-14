package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

/**
 * Scala 3 union type, e.g. `Foo | Bar`
 */
final case class ScOrType private (lhs: ScType, rhs: ScType) extends ScalaType with ValueType {
  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitOrType(this)

  override implicit def projectContext: ProjectContext = lhs.projectContext

  //@TODO
  def join: ScType = lhs.lub(rhs)

  override def equivInner(
    r:           ScType,
    constraints: ConstraintSystem,
    falseUndef:  Boolean
  ): ConstraintsResult = r match {
    case ScOrType(rLhs, rRhs) =>
      if (r eq this) constraints
      else {
        val lhsConstraints = lhs.equiv(rLhs, constraints, falseUndef)

        lhsConstraints match {
          case ConstraintsResult.Left =>
            val swapped = lhs.equiv(rRhs, constraints, falseUndef)
            swapped match {
              case ConstraintsResult.Left => ConstraintsResult.Left
              case cs: ConstraintSystem   => rhs.equiv(rLhs, cs, falseUndef)
            }
          case cs: ConstraintSystem   => rhs.equiv(rRhs, cs, falseUndef)
        }
      }
    case _ => ConstraintsResult.Left
  }
}

object ScOrType {
  def apply(lhs: ScType, rhs: ScType): ScType = {
    if (!ScalaApplicationSettings.PRECISE_TEXT && lhs == rhs) lhs.asInstanceOf[ValueType]
    else                                                      new ScOrType(lhs, rhs)
  }
}
