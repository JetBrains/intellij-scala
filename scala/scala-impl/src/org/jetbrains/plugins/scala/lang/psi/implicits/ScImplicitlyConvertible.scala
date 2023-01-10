package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._

/**
  * Utility class for implicit conversions.
 */
object ScImplicitlyConvertible {

  def implicits(place: ScExpression, fromUnderscore: Boolean): Seq[PsiNamedElement] =
    findPlaceType(place, fromUnderscore).toSeq.flatMap { placeType =>
      val results =
        collectRegulars(place, placeType) ++ collectCompanions(placeType, argumentTypes = place.expectedTypes(fromUnderscore), place)
      results.map(_.element).toSeq
    }

  private def findPlaceType(place: ScExpression, fromUnderscore: Boolean): Option[ScType] =
    place.getTypeWithoutImplicits(fromUnderscore = fromUnderscore).toOption
      .map(_.tryExtractDesignatorSingleton)

  private def collectRegulars(place: ScExpression, placeType: ScType): Set[ImplicitConversionResolveResult] = cachedWithRecursionGuard("ScImplicitlyConvertible.collectRegulars", place, Set.empty[ImplicitConversionResolveResult], BlockModificationTracker(place), (place, placeType)) {
    placeType match {
      case _: UndefinedType => Set.empty
      case _ if placeType.isNothing => Set.empty
      case _ =>
        new ImplicitConversionProcessor(place, false)
          .candidatesByPlace
          .flatMap(ImplicitConversionResolveResult.applicable(_, placeType, place))
    }
  }

  private def collectCompanions(placeType: ScType, argumentTypes: Seq[ScType], place: PsiElement): Set[ImplicitConversionResolveResult] = cachedWithRecursionGuard("ScImplicitlyConvertible.collectCompanions", place, Set.empty[ImplicitConversionResolveResult], BlockModificationTracker(place), (placeType, argumentTypes, place)) {
    val expandedType = argumentTypes match {
      case Seq() => placeType
      case seq => TupleType(Seq(placeType) ++ seq)(place.elementScope)
    }

     new ImplicitConversionProcessor(place, true)
      .candidatesByType(expandedType)
      .flatMap(ImplicitConversionResolveResult.applicable(_, placeType, place))
  }

}
