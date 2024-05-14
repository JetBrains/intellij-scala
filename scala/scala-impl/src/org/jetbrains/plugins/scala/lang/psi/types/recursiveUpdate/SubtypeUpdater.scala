package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Contravariant, Covariant, Invariant, JavaArrayType, TypeParameter, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.types._

private abstract class SubtypeUpdater(needVariance: Boolean, needUpdate: Boolean) {

  protected implicit def implicitThis: SubtypeUpdater = this

  private def updateCompoundType(ct: ScCompoundType,
                         variance: Variance,
                         substitutor: ScSubstitutor)
                        (implicit visited: Set[ScType]): ScType = {

    val updSignatureMap = ct.signatureMap.map { case (s: TermSignature, tp) =>
      val tParams = s.typeParams.map(updateTypeParameter(_, substitutor, Invariant))

      val paramTypes =
        s.substitutedTypes.map(_.map(f => () => substitutor.recursiveUpdateImpl(f(), variance, isLazySubtype = true)))

      val updSignature = new TermSignature(
        s.name,
        paramTypes,
        tParams,
        s.substitutor.followed(substitutor),
        s.namedElement,
        s.exportedIn,
        s.hasRepeatedParam
      )

      (updSignature, substitutor.recursiveUpdateImpl(tp, Covariant))
    }

    val updatedTypes = ct.typesMap.map {
      case (s, ta) =>
        val substTps: Seq[TypeParameter] = ta.typeParams.map(updateTypeParameter(_, substitutor))
        val substLower: ScType = substitutor.recursiveUpdateImpl(ta.lowerBound)
        val substUpper: ScType = substitutor.recursiveUpdateImpl(ta.upperBound)
        val combinedSubstitutor: ScSubstitutor = ta.substitutor.followed(substitutor)

        (s, TypeAliasSignature(ta.typeAlias, ta.name, substTps, substLower, substUpper, ta.isDefinition, combinedSubstitutor))
    }
    val updatedComponents = ct.components.smartMap(substitutor.recursiveUpdateImpl(_, variance))

    ScCompoundType(updatedComponents, updSignatureMap, updatedTypes)(ct.projectContext)
  }

  private def updateExistentialArg(exArg: ScExistentialArgument,
                           substitutor: ScSubstitutor)
                          (implicit visited: Set[ScType]): ScType = {
    exArg.copyWithBounds(
      substitutor.recursiveUpdateImpl(exArg.lower, Contravariant, exArg.isLazy),
      substitutor.recursiveUpdateImpl(exArg.upper, Covariant, exArg.isLazy)
    )
  }

  private def updateExistentialType(exType: ScExistentialType,
                            variance: Variance,
                            substitutor: ScSubstitutor)
                           (implicit visited: Set[ScType]): ScType = {
    val quantified = exType.quantified
    val updatedQ = substitutor.recursiveUpdateImpl(quantified, variance)

    if (!needUpdate || (updatedQ eq quantified)) exType
    else ScExistentialType(updatedQ)
  }

  private def updateParameterizedType(pt: ScParameterizedType,
                              variance: Variance,
                              substitutor: ScSubstitutor)
                             (implicit visited: Set[ScType]): ScType = {

    val designator = pt.designator
    val typeArguments = pt.typeArguments
    val typeParameterVariances =
      if (!needVariance) Seq.empty
      else designator.extractDesignated(expandAliases = false) match {
        case Some(n: ScTypeParametersOwner) => n.typeParameters.map(_.variance)
        case _ => Seq.empty
      }
    val newDesignator = substitutor.recursiveUpdateImpl(designator, variance)
    val newTypeArgs = typeArguments.smartMapWithIndex {
      case (ta, i) =>
        val v = if (i < typeParameterVariances.length) typeParameterVariances(i) else Invariant
        substitutor.recursiveUpdateImpl(ta, v * variance)
    }

    if (!needUpdate || (newDesignator eq designator) && (newTypeArgs eq typeArguments)) pt
    else ScParameterizedType(newDesignator, newTypeArgs)
  }


  private def updateJavaArrayType(arrType: JavaArrayType,
                          substitutor: ScSubstitutor)
                         (implicit visited: Set[ScType]): ScType = {
    JavaArrayType(substitutor.recursiveUpdateImpl(arrType.argument, Invariant))
  }

  private def updateProjectionType(pt: ScProjectionType,
                           substitutor: ScSubstitutor)
                          (implicit visited: Set[ScType]): ScType = {

    val projected = pt.projected
    val updatedType = substitutor.recursiveUpdateImpl(projected, Covariant)

    if (!needUpdate || (updatedType eq projected)) pt
    else ScProjectionType(updatedType, pt.element)
  }

  private def updateMethodType(mt: ScMethodType,
                       variance: Variance,
                       substitutor: ScSubstitutor)
                      (implicit visited: Set[ScType]): ScType = {

    def updateParameterType(tp: ScType) = substitutor.recursiveUpdateImpl(tp, -variance, isLazySubtype = true)

    def updateParameter(p: Parameter): Parameter = p.copy(
      paramType = updateParameterType(p.paramType),
      expectedType = updateParameterType(p.expectedType),
      defaultType = p.defaultType.map(updateParameterType)
    )

    ScMethodType(
      substitutor.recursiveUpdateImpl(mt.result, variance),
      mt.params.map(updateParameter),
      mt.isImplicit)(mt.elementScope)
  }

  private def updateTypePolymorphicType(tpt: ScTypePolymorphicType,
                                variance: Variance,
                                substitutor: ScSubstitutor)
                               (implicit visited: Set[ScType]): ScType =
    ScTypePolymorphicType(
      substitutor.recursiveUpdateImpl(tpt.internalType, variance),
      tpt.typeParameters.map(updateTypeParameter(_, substitutor, -variance)),
      isLambdaTypeElement = tpt.isLambdaTypeElement
    )

  private def updateMatchType(mt: ScMatchType,
                              variance: Variance,
                              substitutor: ScSubstitutor)
                             (implicit visited: Set[ScType]): ScType = {
    val scrutinee = substitutor.recursiveUpdateImpl(mt.scrutinee, variance)
    val cases = mt.cases.map(cs => (substitutor.recursiveUpdateImpl(cs._1, variance), substitutor.recursiveUpdateImpl(cs._2, variance)))
    ScMatchType(scrutinee, cases)
  }

  def updateTypeParameter(tp: TypeParameter,
                          substitutor: ScSubstitutor,
                          variance: Variance = Invariant)
                         (implicit visited: Set[ScType]): TypeParameter =
    TypeParameter(
      tp.psiTypeParameter,
      tp.typeParameters.map(updateTypeParameter(_, substitutor, variance)),
      substitutor.recursiveUpdateImpl(tp.lowerType, variance, isLazySubtype = true),
      substitutor.recursiveUpdateImpl(tp.upperType, variance, isLazySubtype = true)
    )

  private def updateAndType(
    andType: ScAndType,
    variance: Variance,
    substitutor: ScSubstitutor
  )(implicit
    visited: Set[ScType]
  ): ScType = {
    val updatedLhs = substitutor.recursiveUpdateImpl(andType.lhs, variance)
    val updatedRhs = substitutor.recursiveUpdateImpl(andType.rhs, variance)

    if (!needUpdate || ((updatedLhs eq andType.lhs) && (updatedRhs eq andType.rhs)))
      andType
    else
      ScAndType(updatedLhs, updatedRhs)
  }

  private def updateOrType(
    orType: ScOrType,
    variance: Variance,
    substitutor: ScSubstitutor
  )(implicit
    visisted: Set[ScType]
  ): ScType = {
    val updatedLhs = substitutor.recursiveUpdateImpl(orType.lhs, variance)
    val updatedRhs = substitutor.recursiveUpdateImpl(orType.rhs, variance)

    if (!needUpdate || ((updatedLhs eq orType.lhs) && (updatedRhs eq orType.rhs)))
      orType
    else
      ScOrType(updatedLhs, updatedRhs)
  }

  final def updateSubtypes(
    scType:      ScType,
    variance:    Variance,
    substitutor: ScSubstitutor
  )(implicit
    visited: Set[ScType]
  ): ScType =
    scType match {
      case t: ScCompoundType        => updateCompoundType(t, variance, substitutor)
      case t: ScOrType              => updateOrType(t, variance, substitutor)
      case t: ScAndType             => updateAndType(t, variance, substitutor)
      case t: ScExistentialArgument => updateExistentialArg(t, substitutor)
      case t: ScExistentialType     => updateExistentialType(t, variance, substitutor)
      case t: ScParameterizedType   => updateParameterizedType(t, variance, substitutor)
      case t: JavaArrayType         => updateJavaArrayType(t, substitutor)
      case t: ScProjectionType      => updateProjectionType(t, substitutor)
      case t: ScMethodType          => updateMethodType(t, variance, substitutor)
      case t: ScTypePolymorphicType => updateTypePolymorphicType(t, variance, substitutor)
      case t: ScMatchType           => updateMatchType(t, variance, substitutor)
      case leaf                     => leaf
    }

  final def recursiveUpdate(scType: ScType, variance: Variance, update: Update): ScType =
    update(scType, variance) match {
      case ReplaceWith(res) => res
      case Stop => scType
      case ProcessSubtypes => updateSubtypes(scType, variance, ScSubstitutor(update))(Set.empty)
    }

}

object SubtypeUpdater {
  implicit class TypeParameterUpdateExt(private val typeParameter: TypeParameter) extends AnyVal {
    def update(substitutor: ScSubstitutor)
              (implicit visited: Set[ScType] = Set.empty): TypeParameter =
      SubtypeUpdaterNoVariance.updateTypeParameter(typeParameter, substitutor)
  }
}

private object SubtypeUpdaterVariance extends SubtypeUpdater(needVariance = true, needUpdate = true)

private object SubtypeUpdaterNoVariance extends SubtypeUpdater(needVariance = false, needUpdate = true)

private object SubtypeTraverser extends SubtypeUpdater(needVariance = false, needUpdate = false)