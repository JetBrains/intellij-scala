package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard

import scala.collection.Set

/**
  * Utility class for implicit conversions.
  *
  * @author alefas, ilyas
  */
object ScImplicitlyConvertible {

  def applicableImplicitConversions(place: ScExpression): collection.Seq[ImplicitResolveResult] =
    findPlaceType(place, fromUnderscore = false) match {
      case None => Seq.empty
      case Some(placeType) =>
        val all = collectRegulars(place, placeType) ++ collectCompanions(placeType, Seq.empty, place)
        all.distinctBy(_.element)
    }

  def implicits(place: ScExpression, fromUnderscore: Boolean): Seq[PsiNamedElement] =
    findPlaceType(place, fromUnderscore).toSeq.flatMap { placeType =>
      val results =
        collectRegulars(place, placeType) ++ collectCompanions(placeType, argumentTypes = place.expectedTypes(fromUnderscore), place)
      results.map(_.element).toSeq
    }

  private def findPlaceType[T](place: ScExpression, fromUnderscore: Boolean): Option[ScType] =
    place.getTypeWithoutImplicits(fromUnderscore = fromUnderscore).toOption
      .map(_.tryExtractDesignatorSingleton)


  private def adaptResults[IR <: ImplicitResolveResult](candidates: Set[ScalaResolveResult], `type`: ScType, place: PsiElement)
                                                       (f: (ScalaResolveResult, ScType, ScSubstitutor) => IR): Set[IR] =
    for {
      resolveResult  <- candidates
      substitutor    =  resolveResult.substitutor
      conversion     <- ImplicitConversionData(resolveResult.element, substitutor)
      application    <- conversion.isApplicable(`type`, place)
      if !application.implicitParameters.exists(_.isNotFoundImplicitParameter)
    } yield f(resolveResult, application.resultType, substitutor)

  @CachedWithRecursionGuard(place, Set.empty, BlockModificationTracker(place))
  private def collectRegulars(place: ScExpression, placeType: ScType): Set[RegularImplicitResolveResult] = {
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

  @CachedWithRecursionGuard(place, Set.empty, BlockModificationTracker(place))
  private def collectCompanions(placeType: ScType, argumentTypes: Seq[ScType], place: PsiElement): Set[CompanionImplicitResolveResult] = {
    val expandedType = argumentTypes match {
      case Seq() => placeType
      case seq => TupleType(Seq(placeType) ++ seq)(place.elementScope)
    }

    val candidates = new ImplicitConversionProcessor(place, true)
      .candidatesByType(expandedType)

    adaptResults(candidates, placeType, place) {
      CompanionImplicitResolveResult
    }
  }

}
