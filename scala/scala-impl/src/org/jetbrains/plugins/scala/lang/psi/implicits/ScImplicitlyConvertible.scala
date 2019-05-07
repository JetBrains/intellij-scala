package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi._
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

  def implicitMap(implicit place: ScExpression): Seq[ImplicitResolveResult] =
    findPlaceType(fromUnderscore = false) { placeType =>
      val seen = mutable.HashSet.empty[PsiNamedElement]
      val buffer = mutable.ArrayBuffer.empty[ImplicitResolveResult]

      for {
        elem <- collectRegulars(placeType)
        if seen.add(elem.element)
      } buffer += elem

      for {
        elem <- collectCompanions(placeType, Seq.empty)
        if seen.add(elem.element)
      } buffer += elem

      buffer
    }

  def implicits(fromUnderscore: Boolean)
               (implicit place: ScExpression): Seq[PsiNamedElement] =
    findPlaceType(fromUnderscore) { placeType =>
      val result = collectRegulars(placeType).map(_.element) ++
        collectCompanions(placeType, arguments = place.expectedTypes(fromUnderscore)).map(_.element)
      result.toSeq
    }

  private def findPlaceType[T](fromUnderscore: Boolean)
                              (collector: ScType => Seq[T])
                              (implicit place: ScExpression) =
    place.getTypeWithoutImplicits(fromUnderscore = fromUnderscore)
      .map(_.tryExtractDesignatorSingleton)
      .fold(
        Function.const(Seq.empty[T]),
        collector
      )

  private def adaptResults[IR <: ImplicitResolveResult](candidates: Set[ScalaResolveResult], `type`: ScType)
                                                       (f: (ScalaResolveResult, ScType, ScSubstitutor) => IR)
                                                       (implicit place: ScExpression): Set[IR] =
    for {
      resolveResult             <- candidates
      (resultType, substitutor) <- targetTypeAndSubstitutor(resolveResult, `type`)
    } yield f(resolveResult, resultType, substitutor)

  @CachedWithRecursionGuard(place, Set.empty, ModCount.getBlockModificationCount)
  private def collectRegulars(placeType: ScType)
                             (implicit place: ScExpression): Set[RegularImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Regular implicit map", LOG)

    placeType match {
      case _: UndefinedType => Set.empty
      case _ if placeType.isNothing => Set.empty
      case _ =>
        val candidates = new ImplicitConversionProcessor(place, false)
          .candidatesByPlace

        adaptResults(candidates, placeType) {
          RegularImplicitResolveResult(_, _, _)
        }
    }
  }

  @CachedWithRecursionGuard(place, Set.empty, ModCount.getBlockModificationCount)
  private def collectCompanions(placeType: ScType, arguments: Seq[ScType])
                               (implicit place: ScExpression): Set[CompanionImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Companions implicit map", LOG)

    val expandedType = arguments match {
      case Seq() => placeType
      case seq => TupleType(Seq(placeType) ++ seq)(place.elementScope)
    }

    val candidates = new ImplicitConversionProcessor(place, true)
      .candidatesByType(expandedType)

    adaptResults(candidates, placeType) {
      CompanionImplicitResolveResult
    }
  }

  def targetTypeAndSubstitutor(conversion: ImplicitConversionData, fromType: ScType)
                              (implicit expression: ScExpression): Option[(ScType, ScSubstitutor)] = {

    ScalaPsiUtil.debug(s"Check implicit: $conversion for type: $fromType", LOG)
    conversion.isCompatible(fromType) match {
      case Right(compatibleResult) =>
        ScalaPsiUtil.debug(s"Implicit $conversion is compatible for type $fromType", LOG)
        Some(compatibleResult)
      case Left(msg) =>
        ScalaPsiUtil.debug(msg, LOG)
        None
    }
  }

  def targetTypeAndSubstitutor(resolveResult: ScalaResolveResult, fromType: ScType)
                              (implicit expression: ScExpression): Option[(ScType, ScSubstitutor)] = {
    ImplicitConversionData(resolveResult)
      .flatMap(targetTypeAndSubstitutor(_, fromType))
  }

}
