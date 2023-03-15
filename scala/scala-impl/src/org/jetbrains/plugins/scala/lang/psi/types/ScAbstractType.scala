package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * This type works like undefined type, but you cannot use this type
 * to resolve generics. It's important if two local type
 * inferences work together.
 */
final class ScAbstractType(val typeParameter: TypeParameter) extends ScalaType with NonValueType with LeafType {

  override implicit def projectContext: ProjectContext = typeParameter.psiTypeParameter

  def lower: ScType = typeParameter.lowerType

  def upper: ScType = typeParameter.upperType

  override def hashCode: Int = typeParameter.hashCode()

  override def equals(obj: scala.Any): Boolean = obj match {
    case other: ScAbstractType => typeParameter == other.typeParameter
    case _ => false
  }

  override def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    r match {
      case _ if falseUndef => ConstraintsResult.Left
      case _ =>
        val conformsUpper = r.conforms(upper, constraints)
        if (conformsUpper.isLeft) return ConstraintsResult.Left

        lower.conforms(r, conformsUpper.constraints)
    }
  }

  override def inferValueType: TypeParameterType = TypeParameterType(typeParameter)

  def simplifyType: ScType = {
    if (upper.equiv(Any)) lower else if (lower.equiv(Nothing)) upper else lower
  }

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitAbstractType(this)
}

object ScAbstractType {
  def unapply(arg: ScAbstractType): Option[(TypeParameter, ScType, ScType)] =
    Some(arg.typeParameter, arg.lower, arg.upper)

  def apply(tp: TypeParameter, lower: ScType, upper: ScType): ScAbstractType = {
    val abstractTp = TypeParameter(tp.psiTypeParameter, tp.typeParameters, lower, upper)
    new ScAbstractType(abstractTp)
  }
}