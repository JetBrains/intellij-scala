package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

import scala.collection.{Set, mutable}

/**
  * Utility class for implicit conversions.
  *
  * @author alefas, ilyas
  */
//todo: refactor this terrible code
object ScImplicitlyConvertible {

  private val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible")

  def implicitMap(place: ScExpression): Seq[ImplicitResolveResult] =
    findPlaceType(place, fromUnderscore = false).toSeq.flatMap { placeType =>
      val seen = mutable.HashSet.empty[PsiNamedElement]
      val buffer = mutable.ArrayBuffer.empty[ImplicitResolveResult]

      for {
        elem <- collectRegulars(place, placeType)
        if seen.add(elem.element)
      } buffer += elem

      for {
        elem <- collectCompanions(placeType, Seq.empty, place)
        if seen.add(elem.element)
      } buffer += elem

      buffer
    }

  def implicits(place: ScExpression, fromUnderscore: Boolean): Seq[PsiNamedElement] =
    findPlaceType(place, fromUnderscore).toSeq.flatMap { placeType =>
      val result = collectRegulars(place, placeType).map(_.element) ++
        collectCompanions(placeType, arguments = place.expectedTypes(fromUnderscore), place).map(_.element)
      result.toSeq
    }

  private def findPlaceType[T](place: ScExpression, fromUnderscore: Boolean): Option[ScType] =
    place.getTypeWithoutImplicits(fromUnderscore = fromUnderscore).toOption
      .map(_.tryExtractDesignatorSingleton)


  private def adaptResults[IR <: ImplicitResolveResult](candidates: Set[ScalaResolveResult], `type`: ScType, place: PsiElement)
                                                       (f: (ScalaResolveResult, ScType, ScSubstitutor) => IR): Set[IR] =
    for {
      resolveResult             <- candidates
      (resultType, substitutor) <- targetTypeAndSubstitutor(resolveResult, `type`, place)
    } yield f(resolveResult, resultType, substitutor)

  @CachedWithRecursionGuard(place, Set.empty, ModCount.getBlockModificationCount)
  private def collectRegulars(place: ScExpression, placeType: ScType): Set[RegularImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Regular implicit map", LOG)

    placeType match {
      case _: UndefinedType => Set.empty
      case _ if placeType.isNothing => Set.empty
      case _ =>
        val candidates = new ImplicitConversionProcessor(place, false)
          .candidatesByPlace

        adaptResults(candidates, placeType, place) {
          RegularImplicitResolveResult(_, _, _)
        }
    }
  }

  @CachedWithRecursionGuard(place, Set.empty, ModCount.getBlockModificationCount)
  private def collectCompanions(placeType: ScType, arguments: Seq[ScType], place: PsiElement): Set[CompanionImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Companions implicit map", LOG)

    val expandedType = arguments match {
      case Seq() => placeType
      case seq => TupleType(Seq(placeType) ++ seq)(place.elementScope)
    }

    val candidates = new ImplicitConversionProcessor(place, true)
      .candidatesByType(expandedType)

    adaptResults(candidates, placeType, place) {
      CompanionImplicitResolveResult
    }
  }

  def targetTypeAndSubstitutor(conversion: ImplicitConversionData,
                               fromType: ScType,
                               place: PsiElement): Option[(ScType, ScSubstitutor)] = {

    ScalaPsiUtil.debug(s"Check implicit: $conversion for type: $fromType", LOG)
    conversion.isCompatible(fromType, place) match {
      case Right(compatibleResult) =>
        ScalaPsiUtil.debug(s"Implicit $conversion is compatible for type $fromType", LOG)
        Some(compatibleResult)
      case Left(msg) =>
        ScalaPsiUtil.debug(msg, LOG)
        None
    }
  }

  def targetTypeAndSubstitutor(resolveResult: ScalaResolveResult,
                               fromType: ScType,
                               place: PsiElement): Option[(ScType, ScSubstitutor)] =
    targetTypeAndSubstitutor(resolveResult.element, resolveResult.substitutor, fromType, place)

  def targetTypeAndSubstitutor(element: PsiNamedElement,
                               substitutor: ScSubstitutor,
                               fromType: ScType,
                               place: PsiElement): Option[(ScType, ScSubstitutor)] = {
    ImplicitConversionData(element, substitutor)
      .flatMap(targetTypeAndSubstitutor(_, fromType, place))
  }

}
