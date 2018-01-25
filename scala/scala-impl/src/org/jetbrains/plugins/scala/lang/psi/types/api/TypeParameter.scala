package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * Class representing type parameters in our type system. Can be constructed from psi.
  *
  * lowerType and upperType sometimes should be lazy, see SCL-7216
  */
sealed trait TypeParameter {
  val typeParameters: Seq[TypeParameter]
  def lowerType: ScType
  def upperType: ScType
  val psiTypeParameter: PsiTypeParameter

  def name: String = psiTypeParameter.name

  def update(function: ScType => ScType): TypeParameter = TypeParameter.StrictTp(
    typeParameters.map(_.update(function)),
    function(lowerType),
    function(upperType),
    psiTypeParameter)

  def updateWithVariance(function: (ScType, Variance) => ScType, variance: Variance): TypeParameter = TypeParameter.StrictTp(
    typeParameters.map(_.updateWithVariance(function, variance)),
    function(lowerType, variance),
    function(upperType, -variance),
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

  def unapply(tp: TypeParameter): Option[(Seq[TypeParameter], ScType, ScType, PsiTypeParameter)] =
    Some(tp.typeParameters, tp.lowerType, tp.upperType, tp.psiTypeParameter)

  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = {
    val manager = ScalaPsiManager.instance(typeParameter.getProject)
    manager.javaPsiTypeParameterUpperType(typeParameter)
  }

  private case class StrictTp(typeParameters: Seq[TypeParameter],
                              override val lowerType: ScType,
                              override val upperType: ScType,
                              psiTypeParameter: PsiTypeParameter) extends TypeParameter

  private case class LazyScalaTp(psiTypeParameter: ScTypeParam) extends TypeParameter {
    override val typeParameters: Seq[TypeParameter] = psiTypeParameter.typeParameters.map(TypeParameter(_))

    override lazy val lowerType: ScType = psiTypeParameter.lowerBound.getOrNothing

    override lazy val upperType: ScType = psiTypeParameter.upperBound.getOrAny
  }

  private case class LazyJavaTp(psiTypeParameter: PsiTypeParameter) extends TypeParameter {
    override val typeParameters: Seq[TypeParameter] = Seq.empty

    override val lowerType: ScType = Nothing(psiTypeParameter.getProject)

    override lazy val upperType: ScType = TypeParameter.javaPsiTypeParameterUpperType(psiTypeParameter)
  }
}