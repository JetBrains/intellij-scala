package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScSubstitutor, ScType, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.Seq

sealed trait TypeParameterType extends ValueType with NamedType {
  val arguments: Seq[TypeParameterType]

  val lowerType: Suspension

  val upperType: Suspension

  def psiTypeParameter: PsiTypeParameter

  override implicit def projectContext: ProjectContext = psiTypeParameter

  override val name: String = psiTypeParameter.name

  def nameAndId: (String, Long) = psiTypeParameter.nameAndId

  def isInvariant: Boolean = psiTypeParameter match {
    case typeParam: ScTypeParam => !typeParam.isCovariant && !typeParam.isContravariant
    case _ => false
  }

  def isCovariant: Boolean = psiTypeParameter match {
    case typeParam: ScTypeParam => typeParam.isCovariant
    case _ => false
  }

  def isContravariant: Boolean = psiTypeParameter match {
    case typeParam: ScTypeParam => typeParam.isContravariant
    case _ => false
  }

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) =
    (`type` match {
      case that: TypeParameterType => (that.psiTypeParameter eq psiTypeParameter) || {
        (psiTypeParameter, that.psiTypeParameter) match {
          case (myBound: ScTypeParam, thatBound: ScTypeParam) =>
            //TODO this is a temporary hack, so ignore substitutor for now
            myBound.lowerBound.exists(_.equiv(thatBound.lowerBound.getOrNothing)) &&
              myBound.upperBound.exists(_.equiv(thatBound.upperBound.getOrNothing)) &&
              (myBound.name == thatBound.name || thatBound.isHigherKindedTypeParameter || myBound.isHigherKindedTypeParameter)
          case _ => false
        }
      }
      case _ => false
    }, substitutor)

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitTypeParameterType(this)
}

object TypeParameterType {
  def apply(tp: TypeParameter): TypeParameterType = LazyTpt(tp, Some(ScSubstitutor.empty))

  def apply(psiTp: PsiTypeParameter, maybeSubstitutor: Option[ScSubstitutor] = Some(ScSubstitutor.empty)): TypeParameterType =
    LazyTpt(TypeParameter(psiTp), maybeSubstitutor)

  def apply(arguments: Seq[TypeParameterType],
            lowerType: ScType,
            upperType: ScType,
            psiTypeParameter: PsiTypeParameter): TypeParameterType = StrictTpt(arguments, lowerType, upperType, psiTypeParameter)

  def unapply(tpt: TypeParameterType): Option[(Seq[TypeParameterType], Suspension, Suspension, PsiTypeParameter)] =
    Some(tpt.arguments, tpt.lowerType, tpt.upperType, tpt.psiTypeParameter)


  private case class LazyTpt(typeParameter: TypeParameter, maybeSubstitutor: Option[ScSubstitutor] = Some(ScSubstitutor.empty))
    extends TypeParameterType {

    val arguments: Seq[TypeParameterType] = typeParameter.typeParameters.map(LazyTpt(_, maybeSubstitutor))

    val lowerType: Suspension = lift(typeParameter.lowerType)

    val upperType: Suspension = lift(typeParameter.upperType)

    def psiTypeParameter: PsiTypeParameter = typeParameter.psiTypeParameter

    private def lift(s: Suspension): Suspension = maybeSubstitutor match {
      case Some(substitutor) => Suspension(() => substitutor.subst(s.v))
      case _ => s
    }
  }

  private case class StrictTpt(arguments: Seq[TypeParameterType],
                               lType: ScType,
                               uType: ScType,
                               psiTypeParameter: PsiTypeParameter) extends TypeParameterType {

    override val lowerType: Suspension = Suspension(lType)

    override val upperType: Suspension = Suspension(uType)
  }
}
