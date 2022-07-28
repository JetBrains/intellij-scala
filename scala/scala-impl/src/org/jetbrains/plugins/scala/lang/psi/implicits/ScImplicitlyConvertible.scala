package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard

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

  @CachedWithRecursionGuard(place, Set.empty, BlockModificationTracker(place))
  private def collectRegulars(place: ScExpression, placeType: ScType): Set[ImplicitConversionResolveResult] = {
    placeType match {
      case _: UndefinedType => Set.empty
      case _ if placeType.isNothing => Set.empty
      case _ =>
        new ImplicitConversionProcessor(place, false)
          .candidatesByPlace
          .flatMap(ImplicitConversionResolveResult.applicable(_, placeType, place))
    }
  }

  @CachedWithRecursionGuard(place, Set.empty, BlockModificationTracker(place))
  private def collectCompanions(placeType: ScType, argumentTypes: Seq[ScType], place: PsiElement): Set[ImplicitConversionResolveResult] = {
    val expandedType = argumentTypes match {
      case Seq() => placeType
      case seq => TupleType(Seq(placeType) ++ seq)(place.elementScope)
    }

     new ImplicitConversionProcessor(place, true)
      .candidatesByType(expandedType)
      .flatMap(ImplicitConversionResolveResult.applicable(_, placeType, place))
  }

}
