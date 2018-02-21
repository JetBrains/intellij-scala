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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}
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
                              val fromUnderscore: Boolean = false) {

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

  @CachedWithRecursionGuard(expression, Set.empty, ModCount.getBlockModificationCount)
  private def collectRegulars: Set[RegularImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Regular implicit map", LOG)

    placeType match {
      case None => Set.empty
      case Some(t) if t.isNothing => Set.empty
      case Some(u: UndefinedType) => Set.empty
      case Some(scType) =>
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

        adaptResults(processor.candidatesS, scType).map {
          case (result, tp, substitutor) => RegularImplicitResolveResult(result, tp, substitutor)
        }
    }
  }

  @CachedWithRecursionGuard(expression, Set.empty, ModCount.getBlockModificationCount)
  private def collectCompanions(arguments: Seq[ScType]): Set[CompanionImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Companions implicit map", LOG)

    placeType match {
      case None => Set.empty
      case Some(scType) =>
        implicit val elementScope = expression.elementScope
        val expandedType = arguments match {
          case Seq() => scType
          case seq => TupleType(Seq(scType) ++ seq)
        }

        val processor = new CollectImplicitsProcessor(expression, true)
        ScalaPsiUtil.collectImplicitObjects(expandedType).foreach {
          processor.processType(_, expression, ResolveState.initial())
        }

        adaptResults(processor.candidatesS, scType).map {
          case (result, tp, substitutor) => CompanionImplicitResolveResult(result, tp, substitutor)
        }
    }
  }
}

object ScImplicitlyConvertible {
  private implicit val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible")

  def forMap(expression: ScExpression, result: ScalaResolveResult, `type`: ScType): Option[ImplicitMapResult] = {
    import expression.projectContext

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

    val argumentType = firstParameter.`type`()

    def substitute(maybeType: TypeResult) =
      maybeType.map(substitutor.subst)
        .getOrNothing

    (substitute(argumentType), substitute(function.returnType))
  }

  private def getTypes(expression: ScExpression, substitutor: ScSubstitutor, element: PsiNamedElement): Option[(ScType, ScType)] = {
    import expression.projectContext

    val funType = expression.elementScope.cachedFunction1Type.getOrElse {
      return None
    }

    val maybeElementType = (element match {
      case f: ScFunction =>
        f.returnType
      case _: ScBindingPattern | _: ScParameter | _: ScObject =>
        // View Bounds and Context Bounds are processed as parameters.
        element.asInstanceOf[Typeable].`type`()
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
                                 undefinedSubstitutor: ScUndefinedSubstitutor) = {

    var uSubst = undefinedSubstitutor
    uSubst.getSubstitutor match {
      case Some(unSubst) =>
        val typeParamIds = function.typeParameters.map(_.typeParamId).toSet
        def hasRecursiveTypeParameters(`type`: ScType): Boolean = `type`.hasRecursiveTypeParameters(typeParamIds)

        def substitute(maybeType: TypeResult) = maybeType
          .toOption
          .map(substitutor.subst)
          .map(unSubst.subst)
          .withFilter(!hasRecursiveTypeParameters(_))

        function.typeParameters.foreach { typeParameter =>
          val typeParamId = typeParameter.typeParamId

          substitute(typeParameter.lowerBound).foreach { lower =>
            uSubst = uSubst.addLower(typeParamId, lower, additional = true)
          }

          substitute(typeParameter.upperBound).foreach { upper =>
            uSubst = uSubst.addUpper(typeParamId, upper, additional = true)
          }
        }

        def createDependentSubstitutors(unSubst: ScSubstitutor) = {
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

          val implicitDependentSubstitutor = ScSubstitutor(() => {
            val (inferredParameters, expressions, _) = findImplicits(effectiveParameters, None, expression, canThrowSCE = false,
              abstractSubstitutor = substitutor.followed(dependentSubstitutor).followed(unSubst))

            val inferredTypes = expressions.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None))
              .map(_._1.getOrAny)

            inferredParameters.zip(inferredTypes).toMap
          })

          (dependentSubstitutor, implicitDependentSubstitutor)

        }

        uSubst.getSubstitutor
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

  object ImplicitMapResult {

    def unapply(mapResult: ImplicitMapResult): Option[(ScalaResolveResult, ScType)] =
      Option(mapResult).map { result =>
        (result.resolveResult, result.resultType)
      }
  }

  private case class SimpleImplicitMapResult(resolveResult: ScalaResolveResult, resultType: ScType) extends ImplicitMapResult

  private case class DependentImplicitMapResult(resolveResult: ScalaResolveResult, resultType: ScType,
                                                undefinedSubstitutor: ScUndefinedSubstitutor,
                                                override val implicitDependentSubstitutor: ScSubstitutor) extends ImplicitMapResult

}
