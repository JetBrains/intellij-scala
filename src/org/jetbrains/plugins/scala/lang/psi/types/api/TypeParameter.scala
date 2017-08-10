package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Class representing type parameters in our type system. Can be constructed from psi.
  *
  * lowerType and upperType sometimes should be lazy, see SCL-7216
  */
sealed trait TypeParameter {
  val typeParameters: Seq[TypeParameter]
  val lowerType: Suspension
  val upperType: Suspension
  val psiTypeParameter: PsiTypeParameter

  def name: String = psiTypeParameter.name

  def nameAndId: (String, Long) = psiTypeParameter.nameAndId

  def update(function: ScType => ScType): TypeParameter = TypeParameter.StrictTp(
    typeParameters.map(_.update(function)),
    function(lowerType.v),
    function(upperType.v),
    psiTypeParameter)

  def updateWithVariance(function: (ScType, Variance) => ScType, variance: Variance): TypeParameter = TypeParameter.StrictTp(
    typeParameters.map(_.updateWithVariance(function, variance)),
    function(lowerType.v, variance),
    function(upperType.v, -variance),
    psiTypeParameter)
}

object TypeParameter {
  def apply(typeParameter: PsiTypeParameter): TypeParameter = typeParameter match {
    case typeParam: ScTypeParam => LazyScalaTp(typeParam)
    case _ => LazyJavaTp(typeParameter)
  }

  def apply(typeParameters: Seq[TypeParameter],
            lType: ScType,
            uType: ScType,
            psiTypeParameter: PsiTypeParameter): TypeParameter = StrictTp(typeParameters, lType, uType, psiTypeParameter)

  def unapply(tp: TypeParameter): Option[(Seq[TypeParameter], Suspension, Suspension, PsiTypeParameter)] =
    Some(tp.typeParameters, tp.lowerType, tp.upperType, tp.psiTypeParameter)

  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = {
    val manager = ScalaPsiManager.instance(typeParameter.getProject)
    manager.javaPsiTypeParameterUpperType(typeParameter)
  }

  private case class StrictTp(typeParameters: Seq[TypeParameter],
                            lType: ScType,
                            uType: ScType,
                            psiTypeParameter: PsiTypeParameter) extends TypeParameter {
    override val lowerType: Suspension = Suspension(lType)

    override val upperType: Suspension = Suspension(uType)
  }

  private case class LazyScalaTp(psiTypeParameter: ScTypeParam) extends TypeParameter {
    override val typeParameters: Seq[TypeParameter] = psiTypeParameter.typeParameters.map(TypeParameter(_))

    override val lowerType: Suspension = Suspension(() => psiTypeParameter.lowerBound.getOrNothing)

    override val upperType: Suspension = Suspension(() => psiTypeParameter.upperBound.getOrAny)
  }

  private case class LazyJavaTp(psiTypeParameter: PsiTypeParameter) extends TypeParameter {
    override val typeParameters: Seq[TypeParameter] = Seq.empty

    override val lowerType: Suspension = Suspension(Nothing(psiTypeParameter.getProject))

    override val upperType: Suspension = Suspension(() => TypeParameter.javaPsiTypeParameterUpperType(psiTypeParameter))
  }
}