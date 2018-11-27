package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.findImplicits
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
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
      resolveResult <- candidates
      (resultType, substitutor) <- forMap(resolveResult, `type`)
    } yield f(resolveResult, resultType, substitutor)

  @CachedWithRecursionGuard(place, Set.empty, ModCount.getBlockModificationCount)
  private def collectRegulars(placeType: ScType)
                             (implicit place: ScExpression): Set[RegularImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Regular implicit map", LOG)

    placeType match {
      case _: UndefinedType => Set.empty
      case _ if placeType.isNothing => Set.empty
      case _ =>
        val candidates = new CollectImplicitsProcessor(place, false)
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

    val candidates = new CollectImplicitsProcessor(place, true)
      .candidatesByType(expandedType)

    adaptResults(candidates, placeType) {
      CompanionImplicitResolveResult
    }
  }

  def forMap(result: ScalaResolveResult, `type`: ScType)
            (implicit expression: ScExpression): Option[(ScType, ScSubstitutor)] = {
    ScalaPsiUtil.debug(s"Check implicit: $result for type: ${`type`}", LOG)

    val ScalaResolveResult(element, substitutor) = result

    if (PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(element), expression, false)) return None

    //to prevent infinite recursion
    ProgressManager.checkCanceled()

    val (tp: ScType, resultType: ScType) = element match {
      case f: ScFunction if f.paramClauses.clauses.nonEmpty => getTypes(substitutor, f)
      case _ => getTypes(substitutor, element).getOrElse(return None)
    }

    val newSubstitutor = element match {
      case f: ScFunction => ScalaPsiUtil.inferMethodTypesArgs(f, substitutor)
      case _ => ScSubstitutor.empty
    }

    val substituted = newSubstitutor(tp)
    if (!`type`.weakConforms(substituted)) {
      ScalaPsiUtil.debug(s"Implicit $result doesn't conform to ${`type`}", LOG)
      return None
    }

    val mapResult = element match {
      case function: ScFunction if function.hasTypeParameters =>
        val constraints = `type`.conforms(substituted, ConstraintSystem.empty).constraints
        createSubstitutors(function, `type`, substitutor, constraints, resultType)
      case _ => Some(resultType, ScSubstitutor.empty)
    }

    val message = mapResult match {
      case Some(_) => "is ok"
      case _ => "has problems with type parameters bounds"
    }

    ScalaPsiUtil.debug(s"Implicit $result $message for type ${`type`}", LOG)

    mapResult
  }

  private def getTypes(substitutor: ScSubstitutor, function: ScFunction) = {
    val clause = function.paramClauses.clauses.head
    val firstParameter = clause.parameters.head

    val argumentType = firstParameter.`type`()

    def substitute(maybeType: TypeResult) =
      maybeType.map(substitutor)
        .getOrNothing

    (substitute(argumentType), substitute(function.returnType))
  }

  private def getTypes(substitutor: ScSubstitutor, element: PsiNamedElement)
                      (implicit place: ScExpression): Option[(ScType, ScType)] = {
    val maybeElementType = (element match {
      case f: ScFunction => f.returnType
      case _: ScBindingPattern | _: ScParameter | _: ScObject =>
        // View Bounds and Context Bounds are processed as parameters.
        element.asInstanceOf[Typeable].`type`()
    }).toOption

    for {
      funType <- place.elementScope.cachedFunction1Type
      elementType <- maybeElementType

      substitution = substitutor(elementType).conforms(funType, ConstraintSystem.empty) match {
        case ConstraintSystem(newSubstitutor) => newSubstitutor
        case _ => Function.const(Nothing) _
      }

      argumentType :: resultType :: Nil = funType.typeArguments.toList
        .map(substitution)
    } yield (argumentType, resultType)
  }

  private def createSubstitutors(function: ScFunction,
                                 `type`: ScType,
                                 substitutor: ScSubstitutor,
                                 constraints: ConstraintSystem,
                                 resultType: ScType)
                                (implicit place: ScExpression) = constraints match {
    case ConstraintSystem(unSubst) =>
      val typeParamIds = function.typeParameters.map(_.typeParamId).toSet

      def hasRecursiveTypeParameters(`type`: ScType): Boolean = `type`.hasRecursiveTypeParameters(typeParamIds)

      var lastConstraints = constraints

      def substitute(maybeType: TypeResult) = maybeType
        .toOption
        .map(substitutor)
        .map(unSubst)
        .withFilter(!hasRecursiveTypeParameters(_))

      function.typeParameters.foreach { typeParameter =>
        val typeParamId = typeParameter.typeParamId

        substitute(typeParameter.lowerBound).foreach { lower =>
          lastConstraints = lastConstraints.withLower(typeParamId, lower)
        }

        substitute(typeParameter.upperBound).foreach { upper =>
          lastConstraints = lastConstraints.withUpper(typeParamId, upper)
        }
      }

      lastConstraints match {
        case ConstraintSystem(_) =>
          val clauses = function.paramClauses.clauses

          val parameters = clauses.headOption.toSeq.flatMap(_.parameters).map(Parameter(_))

          val dependentSubstitutor = ScSubstitutor.paramToType(parameters, Seq.fill(parameters.length)(`type`))

          def dependentMethodTypes: Option[ScParameterClause] =
            function.returnType.toOption.flatMap { functionType =>
              clauses match {
                case Seq(_, last) if last.isImplicit =>
                  var result: Option[ScParameterClause] = None
                  functionType.recursiveUpdate { t =>
                    t match {
                      case ScDesignatorType(p: ScParameter) if last.parameters.contains(p) =>
                        result = Some(last)
                        Stop
                      case _ =>
                    }
                    if (result.isDefined) Stop
                    else ProcessSubtypes
                  }

                  result
                case _ => None
              }
            }

          val effectiveParameters = dependentMethodTypes.toSeq
            .flatMap(_.effectiveParameters)
            .map(Parameter(_))

          val (inferredParameters, expressions, _) = findImplicits(effectiveParameters, None, place, canThrowSCE = false,
            abstractSubstitutor = substitutor.followed(dependentSubstitutor).followed(unSubst))

          lastConstraints match {
            case ConstraintSystem(lastSubstitutor) =>
              Some(
                lastSubstitutor(dependentSubstitutor(resultType)),
                ScSubstitutor.paramToExprType(inferredParameters, expressions, useExpected = false)
              )
            case _ => None
          }
        case _ => None
      }
    case _ => None
  }
}
