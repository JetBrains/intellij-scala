package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{NamedType, ScSubstitutor, ScType, ScUndefinedSubstitutor}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.Seq

sealed trait TypeParameterType extends ValueType with NamedType {
  val arguments: Seq[TypeParameterType]

  def lowerType: ScType

  def upperType: ScType

  def psiTypeParameter: PsiTypeParameter

  override implicit def projectContext: ProjectContext = psiTypeParameter

  override val name: String = psiTypeParameter.name

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
  def apply(tp: TypeParameter): TypeParameterType = LazyTpt(tp, None)

  def apply(psiTp: PsiTypeParameter): TypeParameterType = LazyTpt(TypeParameter(psiTp), None)

  def apply(psiTp: PsiTypeParameter, substitutor: ScSubstitutor): TypeParameterType =
    LazyTpt(TypeParameter(psiTp), Some(substitutor))

  def apply(arguments: Seq[TypeParameterType],
            lowerType: ScType,
            upperType: ScType,
            psiTypeParameter: PsiTypeParameter): TypeParameterType = StrictTpt(arguments, lowerType, upperType, psiTypeParameter)

  def unapply(tpt: TypeParameterType): Option[(Seq[TypeParameterType], ScType, ScType, PsiTypeParameter)] =
    Some(tpt.arguments, tpt.lowerType, tpt.upperType, tpt.psiTypeParameter)


  private case class LazyTpt(typeParameter: TypeParameter, maybeSubstitutor: Option[ScSubstitutor] = None)
    extends TypeParameterType {

    val arguments: Seq[TypeParameterType] = typeParameter.typeParameters.map(LazyTpt(_, maybeSubstitutor))

    lazy val lowerType: ScType = lift(typeParameter.lowerType)

    lazy val upperType: ScType = lift(typeParameter.upperType)

    def psiTypeParameter: PsiTypeParameter = typeParameter.psiTypeParameter

    private def lift(tp: ScType): ScType = maybeSubstitutor match {
      case Some(s) => s.subst(tp)
      case _ => tp
    }
  }

  private case class StrictTpt(arguments: Seq[TypeParameterType],
                               override val lowerType: ScType,
                               override val upperType: ScType,
                               psiTypeParameter: PsiTypeParameter) extends TypeParameterType
}
