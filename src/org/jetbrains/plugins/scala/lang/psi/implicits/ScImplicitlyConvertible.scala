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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Set, mutable}

/**
  * Utility class for implicit conversions.
  *
  * @author alefas, ilyas
  */
//todo: refactor this terrible code
class ScImplicitlyConvertible(val expression: ScExpression,
                              val fromUnderscore: Boolean = false)
                             (implicit val typeSystem: TypeSystem) {

  private lazy val placeType =
    expression.getTypeWithoutImplicits(fromUnderscore = fromUnderscore).map {
      _.tryExtractDesignatorSingleton
    }.toOption

  import ScImplicitlyConvertible.LOG

  def implicitMap(arguments: Seq[ScType] = Seq.empty): Seq[ImplicitResolveResult] = {
    val seen = new mutable.HashSet[PsiNamedElement]
    val buffer = new ArrayBuffer[ImplicitResolveResult]
    for (elem <- collectRegulars) {
      if (!seen.contains(elem.element)) {
        seen += elem.element
        buffer += elem
      }
    }

    for (elem <- collectCompanions(arguments = arguments)) {
      if (!seen.contains(elem.element)) {
        seen += elem.element
        buffer += elem
      }
    }

    buffer
  }

  private def adaptResults(results: Set[ScalaResolveResult], `type`: ScType): Set[(ScalaResolveResult, ScType, ScSubstitutor)] =
    results.flatMap {
      forMap(expression, _, `type`)
    }.flatMap { result =>
      val returnType = result.resultType

      val maybeType = result match {
        case SimpleImplicitMapResult(_, _) => Some(returnType)
        case DependentImplicitMapResult(_, _, undefinedSubstitutor, _) =>
          undefinedSubstitutor.getSubstitutor
            .map(_.subst(returnType))
      }

      maybeType.map { tp =>
        (result.resolveResult, tp, result.implicitDependentSubstitutor)
      }
    }

  @CachedMappedWithRecursionGuard(expression, Set.empty, ModCount.getBlockModificationCount)
  private def collectRegulars: Set[RegularImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Regular implicit map", LOG)

    val typez = placeType.getOrElse(return Set.empty)

    val processor = new CollectImplicitsProcessor(expression, false)

    // Collect implicit conversions from bottom to up
    def treeWalkUp(p: PsiElement, lastParent: PsiElement) {
      if (p == null) return
      if (!p.processDeclarations(processor,
        ResolveState.initial,
        lastParent, expression)) return
      p match {
        case (_: ScTemplateBody | _: ScExtendsBlock) => //template body and inherited members are at the same level
        case _ => if (!processor.changedLevel) return
      }
      treeWalkUp(p.getContext, p)
    }

    treeWalkUp(expression, null)

    if (typez == Nothing) return Set.empty
    if (typez.isInstanceOf[UndefinedType]) return Set.empty

    adaptResults(processor.candidatesS, typez).map {
      case (result, tp, substitutor) => RegularImplicitResolveResult(result, tp, substitutor)
    }
  }

  @CachedMappedWithRecursionGuard(expression, Set.empty, ModCount.getBlockModificationCount)
  private def collectCompanions(arguments: Seq[ScType]): Set[CompanionImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Companions implicit map", LOG)

    val typez = placeType.getOrElse(return Set.empty)

    implicit val elementScope = expression.elementScope
    val expandedType = arguments match {
      case Seq() => typez
      case seq => TupleType(Seq(typez) ++ seq)
    }

    val processor = new CollectImplicitsProcessor(expression, true)
    ScalaPsiUtil.collectImplicitObjects(expandedType).foreach {
      processor.processType(_, expression, ResolveState.initial())
    }

    adaptResults(processor.candidatesS, typez).map {
      case (result, tp, substitutor) => CompanionImplicitResolveResult(result, tp, substitutor)
    }
  }
}

object ScImplicitlyConvertible {
  private implicit val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible")

  def forMap(expression: ScExpression, result: ScalaResolveResult, `type`: ScType)
            (implicit typeSystem: TypeSystem): Option[ImplicitMapResult] = {
    ScalaPsiUtil.debug(s"Check implicit: $result for type: ${`type`}", LOG)

    if (PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(result.element), expression, false)) return None

    //to prevent infinite recursion
    ProgressManager.checkCanceled()

    val substitutor = result.substitutor
    val (tp: ScType, retTp: ScType) = result.element match {
      case f: ScFunction if f.paramClauses.clauses.nonEmpty => getTypes(substitutor, f)
      case element => getTypes(expression, substitutor, element).getOrElse(return None)
    }

    val newSubstitutor = result.element match {
      case f: ScFunction => ScalaPsiUtil.inferMethodTypesArgs(f, substitutor)
      case _ => ScSubstitutor.empty
    }

    val substituted = newSubstitutor.subst(tp)
    if (!`type`.weakConforms(substituted)) {
      ScalaPsiUtil.debug(s"Implicit $result doesn't conform to ${`type`}", LOG)
      return None
    }

    val mapResult = result.element match {
      case function: ScFunction if function.hasTypeParameters =>
        val (_, undefinedSubstitutor) = `type`.conforms(substituted, ScUndefinedSubstitutor())
        createSubstitutors(expression, function, `type`, substitutor, undefinedSubstitutor).map {
          case (dependentSubst, uSubst, implicitDependentSubst) =>
            DependentImplicitMapResult(result, dependentSubst.subst(retTp), uSubst, implicitDependentSubst)
        }
      case _ =>
        Some(SimpleImplicitMapResult(result, retTp))
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

    val argumentType = firstParameter.getType(TypingContext.empty)

    def substitute(maybeType: TypeResult[ScType]) =
      maybeType.map(substitutor.subst)
        .getOrNothing

    (substitute(argumentType), substitute(function.returnType))
  }

  private def getTypes(expression: ScExpression, substitutor: ScSubstitutor, element: PsiNamedElement)
                      (implicit typeSystem: TypeSystem): Option[(ScType, ScType)] = {
    val funType = expression.elementScope.cachedFunction1Type.getOrElse {
      return None
    }

    val maybeElementType = (element match {
      case f: ScFunction =>
        f.returnType
      case _: ScBindingPattern | _: ScParameter | _: ScObject =>
        // View Bounds and Context Bounds are processed as parameters.
        element.asInstanceOf[Typeable].getType()
    }).toOption

    maybeElementType.map(substitutor.subst)
      .map { leftType =>
        val maybeSubstitutor = leftType.conforms(funType, ScUndefinedSubstitutor())
          ._2.getSubstitutor

        def substitute(`type`: ScType) =
          maybeSubstitutor.map(_.subst(`type`))
            .getOrElse(Nothing)

        val (argumentType, resultType) = funType.typeArguments match {
          case Seq(first, second, _*) => (first, second)
        }

        (substitute(argumentType), substitute(resultType))
      }
  }

  private def createSubstitutors(expression: ScExpression,
                                 function: ScFunction,
                                 `type`: ScType,
                                 substitutor: ScSubstitutor,
                                 undefinedSubstitutor: ScUndefinedSubstitutor)
                                (implicit typeSystem: TypeSystem) = {
    var uSubst = undefinedSubstitutor
    uSubst.getSubstitutor(notNonable = false) match {
      case Some(unSubst) =>
        def hasRecursiveTypeParameters(`type`: ScType): Boolean = {
          var result = false
          `type`.recursiveUpdate {
            case parameterType: TypeParameterType =>
              function.typeParameters
                .find(_.nameAndId == parameterType.nameAndId)
                .foreach { _ =>
                  result = true
                }
              (true, parameterType)
            case updated => (result, updated)
          }
          result
        }

        def substitute(maybeType: TypeResult[ScType]) = maybeType
          .map(substitutor.subst)
          .map(unSubst.subst)
          .filter(!hasRecursiveTypeParameters(_))

        function.typeParameters.foreach { typeParameter =>
          val nameAndId = typeParameter.nameAndId

          substitute(typeParameter.lowerBound).foreach { lower =>
            uSubst = uSubst.addLower(nameAndId, lower, additional = true)
          }

          substitute(typeParameter.upperBound).foreach { upper =>
            uSubst = uSubst.addUpper(nameAndId, upper, additional = true)
          }
        }

        def createDependentSubstitutors(unSubst: ScSubstitutor) = expression.scalaLanguageLevelOrDefault match {
          case level if level >= Scala_2_10 =>
            val clauses = function.paramClauses.clauses

            val parameters = clauses.headOption.toSeq
              .flatMap(_.parameters)
              .map(Parameter(_))

            val dependentSubstitutor = ScSubstitutor(() => {
              parameters.map((_, `type`)).toMap
            })

            def dependentMethodTypes: Option[ScParameterClause] =
              function.returnType.toOption.flatMap { functionType =>
                clauses match {
                  case Seq(_, last) if last.isImplicit =>
                    var result: Option[ScParameterClause] = None
                    functionType.recursiveUpdate { t =>
                      t match {
                        case ScDesignatorType(p: ScParameter) if last.parameters.contains(p) =>
                          result = Some(last)
                        case _ =>
                      }

                      (result.isDefined, t)
                    }

                    result
                  case _ => None
                }
              }

            val effectiveParameters = dependentMethodTypes.toSeq
              .flatMap(_.effectiveParameters)
              .map(Parameter(_))

            val implicitDependentSubstitutor = ScSubstitutor(() => {
              val (inferredParameters, expressions, _) = findImplicits(effectiveParameters, None, expression, check = false,
                abstractSubstitutor = substitutor.followed(dependentSubstitutor).followed(unSubst))

              val inferredTypes = expressions.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None))
                .map(_._1.getOrAny)

              inferredParameters.zip(inferredTypes).toMap
            })

            (dependentSubstitutor, implicitDependentSubstitutor)
          case _ =>
            (ScSubstitutor(() => Map.empty), ScSubstitutor(() => Map.empty))
        }

        uSubst.getSubstitutor(notNonable = false)
          .map(createDependentSubstitutors)
          .map {
            case (dependentSubstitutor, implicitDependentSubstitutor) =>
              //todo: pass implicit parameters
              (dependentSubstitutor, uSubst, implicitDependentSubstitutor)
          }
      case _ => None
    }
  }

  sealed trait ImplicitMapResult {
    val resolveResult: ScalaResolveResult
    val resultType: ScType
    val implicitDependentSubstitutor: ScSubstitutor = ScSubstitutor.empty
  }

  private case class SimpleImplicitMapResult(resolveResult: ScalaResolveResult, resultType: ScType) extends ImplicitMapResult

  private case class DependentImplicitMapResult(resolveResult: ScalaResolveResult, resultType: ScType,
                                                undefinedSubstitutor: ScUndefinedSubstitutor,
                                                override val implicitDependentSubstitutor: ScSubstitutor) extends ImplicitMapResult

}
