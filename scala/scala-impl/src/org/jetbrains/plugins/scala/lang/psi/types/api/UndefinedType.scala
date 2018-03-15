package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Use this type if you want to resolve generics.
  * In conformance using ScUndefinedSubstitutor you can accumulate information
  * about possible generic type.
  */
case class UndefinedType(typeParameter: TypeParameter, level: Int = 0) extends NonValueType {

  override implicit def projectContext: ProjectContext = typeParameter.psiTypeParameter

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitUndefinedType(this)

  def inferValueType: TypeParameterType = TypeParameterType(typeParameter)

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    val result = `type` match {
      case _ if falseUndef => substitutor
      case UndefinedType(_, thatLevel) if thatLevel == level => substitutor
      case UndefinedType(tp, thatLevel) if thatLevel > level =>
        substitutor.addUpper(tp.typeParamId, this)
      case that: UndefinedType if that.level < level =>
        substitutor.addUpper(typeParameter.typeParamId, that)
      case that =>
        val name = typeParameter.typeParamId
        substitutor.addLower(name, that).addUpper(name, that)
    }

    (!falseUndef, result)
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
