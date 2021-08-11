package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, TypeParamIdOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.light.DummyLightTypeParam
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * Class representing type parameters in our type system. Can be constructed from psi.
  *
  * lowerType and upperType sometimes should be lazy, see SCL-7216
  */
sealed trait TypeParameter {
  val psiTypeParameter: PsiTypeParameter
  val typeParameters: Seq[TypeParameter]

  def lowerType: ScType
  def upperType: ScType

  def name: String = psiTypeParameter.name

  def isInvariant: Boolean = psiTypeParameter.asOptionOf[ScTypeParam].exists { t =>
    !t.isCovariant && !t.isContravariant
  }

  def isCovariant: Boolean = psiTypeParameter.asOptionOf[ScTypeParam].exists(_.isCovariant)

  def isContravariant: Boolean = psiTypeParameter.asOptionOf[ScTypeParam].exists(_.isContravariant)

  /**see [[scala.reflect.internal.Variances.varianceInType]]*/
  def varianceInType(scType: ScType): Variance = {
    val thisId = this.typeParamId
    var result: Variance = Bivariant

    scType.recursiveVarianceUpdate(Covariant) {
      case (TypeParameterType(tp), variance: Variance) if thisId == tp.typeParamId =>
        result = result & variance
        Stop
      case _ =>
        ProcessSubtypes
    }
    result
  }
}

object TypeParameter {
  def apply(typeParameter: PsiTypeParameter): TypeParameter = typeParameter match {
    case typeParam: ScTypeParam => ScalaTypeParameter(typeParam)
    case _                      => JavaTypeParameter(typeParameter)
  }

  def apply(psiTypeParameter: PsiTypeParameter,
            typeParameters: Seq[TypeParameter],
            lType: ScType,
            uType: ScType): TypeParameter = StrictTp(psiTypeParameter, typeParameters, lType, uType)

  def light(
    name:           String,
    typeParameters: Seq[TypeParameter],
    lower:          ScType,
    upper:          ScType
  ): TypeParameter =
    LightTypeParameter(name, typeParameters, () => lower, () => upper)

  def deferred(
    name:           String,
    typeParameters: Seq[TypeParameter],
    lower:          () => ScType,
    upper:          () => ScType
  ): TypeParameter =
    LightTypeParameter(name, typeParameters, lower, upper)

  def unapply(tp: TypeParameter): Some[(PsiTypeParameter, Seq[TypeParameter], ScType, ScType)] =
    Some(tp.psiTypeParameter, tp.typeParameters, tp.lowerType, tp.upperType)

  def javaPsiTypeParameterUpperType(typeParameter: PsiTypeParameter): ScType = {
    val manager = ScalaPsiManager.instance(typeParameter.getProject)
    manager.javaPsiTypeParameterUpperType(typeParameter)
  }

  private case class StrictTp(override val psiTypeParameter: PsiTypeParameter,
                              override val typeParameters: Seq[TypeParameter],
                              override val lowerType: ScType,
                              override val upperType: ScType) extends TypeParameter

  private case class ScalaTypeParameter(override val psiTypeParameter: ScTypeParam) extends TypeParameter {
    override val typeParameters: Seq[TypeParameter] = psiTypeParameter.typeParameters.map(ScalaTypeParameter)

    override def lowerType: ScType = psiTypeParameter.lowerBound.getOrNothing

    override def upperType: ScType = psiTypeParameter.upperBound.getOrAny
  }

  private case class JavaTypeParameter(override val psiTypeParameter: PsiTypeParameter) extends TypeParameter {
    override val typeParameters: Seq[TypeParameter] = Seq.empty

    override def lowerType: ScType = Nothing(psiTypeParameter.getProject)

    override def upperType: ScType = javaPsiTypeParameterUpperType(psiTypeParameter)
  }

  private case class LightTypeParameter(
    override val name:           String,
    override val typeParameters: Seq[TypeParameter],
    lower:                       () => ScType,
    upper:                       () => ScType
  ) extends TypeParameter {
    override def lowerType: ScType = lower()
    override def upperType: ScType = upper()

    override lazy val psiTypeParameter: PsiTypeParameter =
      new DummyLightTypeParam(name)(lowerType.projectContext)
  }
}