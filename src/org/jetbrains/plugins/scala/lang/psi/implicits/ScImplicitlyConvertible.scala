package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.{CachedValue, PsiTreeUtil}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.findImplicits
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScClassParameter, ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitMapResult
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ImplicitProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}
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

  def implicitMap(arguments: Seq[ScType] = Seq.empty): (Seq[RegularImplicitResolveResult], Seq[CompanionImplicitResolveResult]) = {
    val seen = new mutable.HashSet[PsiNamedElement]
    val firstBuffer = new ArrayBuffer[RegularImplicitResolveResult]
    for (elem <- collectRegulars) {
      if (!seen.contains(elem.element)) {
        seen += elem.element
        firstBuffer += elem
      }
    }

    val secondBuffer = new ArrayBuffer[CompanionImplicitResolveResult]
    for (elem <- collectCompanions(arguments = arguments)) {
      if (!seen.contains(elem.element)) {
        seen += elem.element
        secondBuffer += elem
      }
    }

    (firstBuffer, secondBuffer)
  }

  private def adaptResults(results: Set[ScalaResolveResult], `type`: ScType): Set[(ScType, ImplicitMapResult)] =
    results.flatMap {
      forMap(_, `type`)
    }.flatMap { result =>
      val returnType = result.rt
      val resolveResult = result.resolveResult

      val maybeType = resolveResult.element match {
        case f: ScFunction if f.hasTypeParameters =>
          result.maybeUndefinedSubstitutor
            .flatMap(_.getSubstitutor)
            .map(_.subst(returnType))
        case _ =>
          Some(returnType)
      }

      maybeType.map { tp =>
        (tp, result)
      }
    }

  @CachedMappedWithRecursionGuard(expression, Set.empty, ModCount.getBlockModificationCount)
  private def collectRegulars: Set[RegularImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Regular implicit map", LOG)

    val typez = placeType.getOrElse(return Set.empty)

    val processor = new CollectImplicitsProcessor(false)

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
      case (tp, result) => RegularImplicitResolveResult(tp, result)
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

    val processor = new CollectImplicitsProcessor(true)
    ScalaPsiUtil.collectImplicitObjects(expandedType).foreach {
      processor.processType(_, expression, ResolveState.initial())
    }

    adaptResults(processor.candidatesS, typez).map {
      case (tp, result) => CompanionImplicitResolveResult(tp, result)
    }
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

  private def getTypes(substitutor: ScSubstitutor, element: PsiNamedElement): Option[(ScType, ScType)] = {
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

  def forMap(result: ScalaResolveResult, `type`: ScType): Option[ImplicitMapResult] = {
    ScalaPsiUtil.debug(s"Check implicit: $result for type: ${`type`}", LOG)

    if (PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(result.element), expression, false)) return None

    //to prevent infinite recursion
    ProgressManager.checkCanceled()

    val substitutor = result.substitutor
    val (tp: ScType, retTp: ScType) = result.element match {
      case f: ScFunction if f.paramClauses.clauses.nonEmpty => getTypes(substitutor, f)
      case element => getTypes(substitutor, element).getOrElse(return None)
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

    result.element match {
      case f: ScFunction if f.hasTypeParameters =>
        createSubstitutors(f, `type`, substituted, substitutor, tp) match {
          case Some((dependentSubst, uSubst, implicitDependentSubst)) =>
            ScalaPsiUtil.debug(s"Implicit $result is ok for type ${`type`}", LOG)
            Some(ImplicitMapResult(result, dependentSubst.subst(retTp), Some(uSubst), implicitDependentSubst))
          case _ =>
            ScalaPsiUtil.debug(s"Implicit $result has problems with type parameters bounds for type ${`type`}", LOG)
            None
        }
      case _ =>
        ScalaPsiUtil.debug(s"Implicit $result is ok for type ${`type`}", LOG)
        Some(ImplicitMapResult(result, retTp))
    }
  }

  private def createSubstitutors(f: ScFunction, `type`: ScType, substituted: ScType,
                                 substitutor: ScSubstitutor, tp: ScType) = {
    var uSubst = `type`.conforms(substituted, ScUndefinedSubstitutor())._2
    uSubst.getSubstitutor(notNonable = false) match {
      case Some(unSubst) =>
        def hasRecursiveTypeParameters(`type`: ScType): Boolean = {
          var result = false
          `type`.recursiveUpdate {
            case parameterType: TypeParameterType =>
              f.typeParameters
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

        f.typeParameters.foreach { typeParameter =>
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
            val clauses = f.paramClauses.clauses

            val parameters = clauses.headOption.toSeq
              .flatMap(_.parameters)
              .map(new Parameter(_))

            val dependentSubstitutor = new ScSubstitutor(() => {
              parameters.map((_, `type`)).toMap
            })

            def dependentMethodTypes: Option[ScParameterClause] =
              f.returnType.toOption.flatMap { functionType =>
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
              .map(new Parameter(_))

            val implicitDependentSubstitutor = new ScSubstitutor(() => {
              val (inferredParameters, expressions, _) = findImplicits(effectiveParameters, None, expression, check = false,
                abstractSubstitutor = substitutor.followed(dependentSubstitutor).followed(unSubst))

              val inferredTypes = expressions.map(_.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None))
                .map(_._1.getOrAny)

              inferredParameters.zip(inferredTypes).toMap
            })

            (dependentSubstitutor, implicitDependentSubstitutor)
          case _ =>
            (new ScSubstitutor(() => Map.empty), new ScSubstitutor(() => Map.empty))
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

  class CollectImplicitsProcessor(withoutPrecedence: Boolean)(implicit override val typeSystem: TypeSystem)
    extends ImplicitProcessor(StdKinds.refExprLastRef, withoutPrecedence) {
    //can be null (in Unit tests or without library)
    private val funType: ScType = {
      val funClass: PsiClass = ScalaPsiManager.instance(getPlace.getProject).getCachedClass(getPlace.getResolveScope, "scala.Function1").orNull
      funClass match {
        case cl: ScTrait => ScParameterizedType(ScalaType.designator(funClass), cl.typeParameters.map(tp =>
          UndefinedType(TypeParameterType(tp))))
        case _ => null
      }
    }

    protected def getPlace: PsiElement = expression

    def execute(element: PsiElement, state: ResolveState): Boolean = {
      def fromType: Option[ScType] = state.get(BaseProcessor.FROM_TYPE_KEY).toOption

      lazy val subst: ScSubstitutor = fromType match {
        case Some(tp) => getSubst(state).followUpdateThisType(tp)
        case _ => getSubst(state)
      }

      element match {
        case named: PsiNamedElement if kindMatches(element) => named match {
          //there is special case for Predef.conforms method
          case f: ScFunction if f.hasModifierProperty("implicit") && !isConformsMethod(f) =>
            if (!ScImplicitlyConvertible.checkFucntionIsEligible(f, getPlace) ||
              !ResolveUtils.isAccessible(f, getPlace)) return true
            val clauses = f.paramClauses.clauses
            //filtered cases
            if (clauses.length > 2) return true
            if (clauses.length == 2) {
              if (!clauses(1).isImplicit) {
                return true
              }
              if (f.hasTypeParameters) {
                val typeParameters = f.typeParameters
                for {
                  param <- clauses(1).parameters
                  paramType <- param.getType(TypingContext.empty)
                } {
                  var hasTypeParametersInType = false
                  paramType.recursiveUpdate {
                    case tp@TypeParameterType(_, _, _, _) if typeParameters.contains(tp.name) =>
                      hasTypeParametersInType = true
                      (true, tp)
                    case tp: ScType if hasTypeParametersInType => (true, tp)
                    case tp: ScType => (false, tp)
                  }
                  if (hasTypeParametersInType) return true //looks like it's not working in compiler 2.10, so it's faster to avoid it
                }
              }
            }
            if (clauses.isEmpty) {
              val rt = subst.subst(f.returnType.getOrElse(return true))
              if (funType == null || !rt.conforms(funType)) return true
            } else if (clauses.head.parameters.length != 1 || clauses.head.isImplicit) return true
            addResult(new ScalaResolveResult(f, subst, getImports(state)))
          case b: ScBindingPattern =>
            ScalaPsiUtil.nameContext(b) match {
              case d: ScDeclaredElementsHolder if (d.isInstanceOf[ScValue] || d.isInstanceOf[ScVariable]) &&
                d.asInstanceOf[ScModifierListOwner].hasModifierProperty("implicit") =>
                if (!ResolveUtils.isAccessible(d.asInstanceOf[ScMember], getPlace)) return true
                val tp = subst.subst(b.getType(TypingContext.empty).getOrElse(return true))
                if (funType == null || !tp.conforms(funType)) return true
                addResult(new ScalaResolveResult(b, subst, getImports(state)))
              case _ => return true
            }
          case param: ScParameter if param.isImplicitParameter =>
            param match {
              case c: ScClassParameter =>
                if (!ResolveUtils.isAccessible(c, getPlace)) return true
              case _ =>
            }
            val tp = subst.subst(param.getType(TypingContext.empty).getOrElse(return true))
            if (funType == null || !tp.conforms(funType)) return true
            addResult(new ScalaResolveResult(param, subst, getImports(state)))
          case obj: ScObject if obj.hasModifierProperty("implicit") =>
            if (!ResolveUtils.isAccessible(obj, getPlace)) return true
            val tp = subst.subst(obj.getType(TypingContext.empty).getOrElse(return true))
            if (funType == null || !tp.conforms(funType)) return true
            addResult(new ScalaResolveResult(obj, subst, getImports(state)))
          case _ =>
        }
        case _ =>
      }
      true
    }
  }

  private def isConformsMethod(f: ScFunction): Boolean = {
    (f.name == "conforms" || f.name == "$conforms") &&
      Option(f.containingClass).flatMap(cls => Option(cls.qualifiedName)).contains("scala.Predef")
  }
}

object ScImplicitlyConvertible {
  private implicit val LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible")

  val IMPLICIT_RESOLUTION_KEY: Key[PsiClass] = Key.create("implicit.resolution.key")
  val IMPLICIT_CONVERSIONS_KEY: Key[CachedValue[collection.Map[ScType, Set[(ScFunctionDefinition, Set[ImportUsed])]]]] = Key.create("implicit.conversions.key")

  val IMPLICIT_REFERENCE_NAME = "implicitReferenceName"
  val IMPLICIT_EXPRESSION_NAME = "implicitExpressionName"
  val IMPLICIT_CALL_TEXT: String = IMPLICIT_REFERENCE_NAME + "(" + IMPLICIT_EXPRESSION_NAME + ")"

  val FAKE_RESOLVE_RESULT_KEY: Key[ScalaResolveResult] = Key.create("fake.resolve.result.key")
  val FAKE_EXPRESSION_TYPE_KEY: Key[ScType] = Key.create("fake.expr.type.key")
  val FAKE_EXPECTED_TYPE_KEY: Key[Option[ScType]] = Key.create("fake.expected.type.key")

  def setupFakeCall(expr: ScMethodCall, rr: ScalaResolveResult, tp: ScType, expected: Option[ScType]) {
    expr.getInvokedExpr.putUserData(FAKE_RESOLVE_RESULT_KEY, rr)
    expr.args.exprs.head.putUserData(FAKE_EXPRESSION_TYPE_KEY, tp)
    expr.putUserData(FAKE_EXPECTED_TYPE_KEY, expected)
  }

  def checkFucntionIsEligible(function: ScFunction, expression: PsiElement): Boolean = {
    if (!function.hasExplicitType) {
      if (PsiTreeUtil.isContextAncestor(function.getContainingFile, expression, false)) {
        val commonContext = PsiTreeUtil.findCommonContext(function, expression)
        if (expression == commonContext) return true //weird case, it covers situation, when function comes from object, not treeWalkUp
        if (function == commonContext) return false
        else {
          var functionContext: PsiElement = function
          while (functionContext.getContext != commonContext) functionContext = functionContext.getContext
          var placeContext: PsiElement = expression
          while (placeContext.getContext != commonContext) placeContext = placeContext.getContext
          (functionContext, placeContext) match {
            case (functionContext: ScalaPsiElement, placeContext: ScalaPsiElement) =>
              val funElem = functionContext.getDeepSameElementInContext
              val conElem = placeContext.getDeepSameElementInContext
              val children = commonContext match {
                case stubPsi: StubBasedPsiElement[_] =>
                  val stub = stubPsi.getStub
                  import scala.collection.JavaConverters._
                  if (stub != null) stub.getChildrenStubs.asScala.map(_.getPsi).toArray
                  else stubPsi.getChildren
                case _ => commonContext.getChildren
              }
              children.find(elem => elem == funElem || elem == conElem) match {
                case Some(elem) if elem == conElem => return false
                case _ =>
              }
            case _ =>
          }
        }
      }
    }
    true
  }

  case class ImplicitMapResult(resolveResult: ScalaResolveResult, rt: ScType,
                               maybeUndefinedSubstitutor: Option[ScUndefinedSubstitutor] = None,
                               implicitDependentSubst: ScSubstitutor = ScSubstitutor.empty)

}
