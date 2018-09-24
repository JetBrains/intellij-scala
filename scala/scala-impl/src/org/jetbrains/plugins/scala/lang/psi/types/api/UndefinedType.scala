package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.{ConstraintsResult, ScType, ConstraintSystem}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Use this type if you want to resolve generics.
  * In conformance using ConstraintSystem you can accumulate information
  * about possible generic type.
  */
case class UndefinedType(typeParameter: TypeParameter, level: Int = 0) extends NonValueType {

  override implicit def projectContext: ProjectContext = typeParameter.psiTypeParameter

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitUndefinedType(this)

  def inferValueType: TypeParameterType = TypeParameterType(typeParameter)

  override def equivInner(`type`: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    if (falseUndef) ConstraintsResult.Failure
    else `type` match {
      case _ if falseUndef => constraints
      case UndefinedType(_, thatLevel) if thatLevel == level => constraints
      case UndefinedType(tp, thatLevel) if thatLevel > level =>
        constraints.withUpper(tp.typeParamId, this)
      case that: UndefinedType if that.level < level =>
        constraints.withUpper(typeParameter.typeParamId, that)
      case that =>
        val name = typeParameter.typeParamId
        constraints.withLower(name, that).withUpper(name, that)
    }
  }
}

object UndefinedType {
  def apply(psiTypeParameter: PsiTypeParameter): UndefinedType =
    UndefinedType(TypeParameter(psiTypeParameter))

  //only one overload can have default arguments :(
  def apply(psiTypeParameter: PsiTypeParameter, level: Int): UndefinedType =
    UndefinedType(TypeParameter(psiTypeParameter), level)

  def apply(typeParameterType: TypeParameterType): UndefinedType =
    UndefinedType(typeParameterType.typeParameter)

}
