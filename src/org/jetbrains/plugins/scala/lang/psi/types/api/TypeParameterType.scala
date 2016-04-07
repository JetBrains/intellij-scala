package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.Suspension
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getPsiElementId
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType, ScUndefinedSubstitutor}

case class TypeParameterType(name: String, arguments: Seq[TypeParameterType],
                             lower: Suspension[ScType], upper: Suspension[ScType],
                             typeParameter: PsiTypeParameter) extends ValueType {
  override def presentableText = name

  override def canonicalText = name

  private var hash: Int = -1

  override def hashCode: Int = {
    if (hash == -1) {
      hash = (((typeParameter.hashCode() * 31 + upper.hashCode) * 31 + lower.hashCode()) * 31 + arguments.hashCode()) * 31 + name.hashCode
    }
    hash
  }

  def nameAndId = getPsiElementId(typeParameter)

  def isInvariant = typeParameter match {
    case typeParam: ScTypeParam => !typeParam.isCovariant && !typeParam.isContravariant
    case _ => false
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    (`type` match {
      case that: TypeParameterType => that.typeParameter eq typeParameter
      case _ => false
    }, substitutor)
  }

  override def visitType(visitor: TypeVisitor) = visitor.visitTypeParameterType(this)
}

object TypeParameterType {
  def apply(typeParameter: PsiTypeParameter, substitutor: ScSubstitutor = ScSubstitutor.empty): TypeParameterType = {
    def lift(function: () => ScType) = new Suspension[ScType](function)

    val (name, typeParameters, lower, upper) = typeParameter match {
      case tp: ScTypeParam =>
        (tp.name,
          tp.typeParameters,
          lift(() => substitutor.subst(tp.lowerBound.getOrNothing)),
          lift(() => substitutor.subst(tp.upperBound.getOrAny))
          )
      case _ =>
        val manager = ScalaPsiManager.instance(typeParameter.getProject)
        (typeParameter.name,
          typeParameter.getTypeParameters.toSeq,
          lift(() => substitutor.subst(manager.psiTypeParameterLowerType(typeParameter))),
          lift(() => substitutor.subst(manager.psiTypeParameterUpperType(typeParameter)))
          )
    }

    TypeParameterType(name, typeParameters.map(TypeParameterType(_, substitutor)), lower, upper, typeParameter)
  }
}
