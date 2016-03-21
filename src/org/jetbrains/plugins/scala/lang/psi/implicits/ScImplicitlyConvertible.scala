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
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
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
class ScImplicitlyConvertible(place: PsiElement, placeType: Boolean => Option[ScType])
                             (implicit val typeSystem: TypeSystem) {
  def this(expr: ScExpression)(implicit typeSystem: TypeSystem) {
    this(expr, fromUnder => {
      //this code is required, because compiler works in the same way
      //otherwise we will see strange error messages like:
      // def foo(x: Map[String, Int]) {}
      // def foo(x: String) {}
      // foo(Map(y -> 1)) //Error is here
      expr.getTypeWithoutImplicits(fromUnderscore = fromUnder).toOption.map {
        case tp =>
          ScalaType.extractDesignatorSingletonType(tp) match {
            case Some(res) => res
            case _ => tp
          }
      }
    })
  }

  import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible._

  def implicitMap(exp: Option[ScType] = None,
                  fromUnder: Boolean = false,
                  args: Seq[ScType] = Seq.empty,
                  exprType: Option[ScType] = None): Seq[ImplicitResolveResult] = {
    val buffer = new ArrayBuffer[ImplicitResolveResult]
    val seen = new mutable.HashSet[PsiNamedElement]
    for (elem <- implicitMapFirstPart(exp, fromUnder, exprType)) {
      if (!seen.contains(elem.element)) {
        seen += elem.element
        buffer += elem
      }
    }
    for (elem <- implicitMapSecondPart(exp, fromUnder, args = args)) {
      if (!seen.contains(elem.element)) {
        seen += elem.element
        buffer += elem
      }
    }
    buffer.toSeq
  }

  @CachedMappedWithRecursionGuard(place, Seq.empty, ModCount.getBlockModificationCount)
  def implicitMapFirstPart(exp: Option[ScType] = None,
                  fromUnder: Boolean = false,
                  exprType: Option[ScType] = None): Seq[ImplicitResolveResult] = {
    buildImplicitMap(exp, fromUnder, isFromCompanion = false, Seq.empty, exprType)
  }

  @CachedMappedWithRecursionGuard(place, Seq.empty, ModCount.getBlockModificationCount)
  def implicitMapSecondPart(exp: Option[ScType] = None,
                            fromUnder: Boolean = false,
                            args: Seq[ScType] = Seq.empty,
                            exprType: Option[ScType] = None): Seq[ImplicitResolveResult] = {
    buildImplicitMap(exp, fromUnder, isFromCompanion = true, args, exprType)
  }

  private def buildImplicitMap(exp: Option[ScType],
                               fromUnder: Boolean = false,
                               isFromCompanion: Boolean,
                               args: Seq[ScType] = Seq.empty,
                               exprType: Option[ScType] = None): Seq[ImplicitResolveResult] = {
    ScalaPsiUtil.debug(s"Implicit map: $exprType, from companion: $isFromCompanion, expected: $exp", LOG)

    val typez: ScType = exprType.getOrElse(placeType(fromUnder).getOrElse(return Seq.empty))

    val buffer = new ArrayBuffer[ImplicitMapResult]
    if (!isFromCompanion) {
      buffer ++= buildSimpleImplicitMap(fromUnder, exprType)
    }
    if (isFromCompanion) {
      val processor = new CollectImplicitsProcessor(true)
      val expandedType: ScType = exp match {
        case Some(expected) =>
          ScFunctionType(expected, Seq(typez) ++ args)(place.getProject, place.getResolveScope)
        case None if args.nonEmpty => ScTupleType(Seq(typez) ++ args)(place.getProject, place.getResolveScope)
        case None => typez
      }
      for (obj <- ScalaPsiUtil.collectImplicitObjects(expandedType, place.getProject, place.getResolveScope)) {
        processor.processType(obj, place, ResolveState.initial())
      }
      for (res <- processor.candidatesS.map(forMap(_, typez)) if res.condition) {
        buffer += res
      }
    }

    val result = new ArrayBuffer[ImplicitResolveResult]

    buffer.foreach{case ImplicitMapResult(_, r, tp, retTp, newSubst, uSubst, implicitDepSusbt) =>
      r.element match {
        case f: ScFunction if f.hasTypeParameters =>
          uSubst.getSubstitutor match {
            case Some(substitutor) =>
              exp match {
                case Some(expected) =>
                  val additionalUSubst = newSubst.subst(retTp).conforms(expected, new ScUndefinedSubstitutor())._2
                  (uSubst + additionalUSubst).getSubstitutor match {
                    case Some(innerSubst) =>
                      result += ImplicitResolveResult(innerSubst.subst(retTp), r.element, r.importsUsed, r.substitutor,
                        implicitDepSusbt, isFromCompanion, Seq.empty)
                    case None =>
                      result += ImplicitResolveResult(substitutor.subst(retTp), r.element, r.importsUsed, r.substitutor,
                        implicitDepSusbt, isFromCompanion, Seq.empty)
                  }
                case None =>
                  result += ImplicitResolveResult(substitutor.subst(retTp), r.element, r.importsUsed, r.substitutor,
                    implicitDepSusbt, isFromCompanion, Seq.empty)
              }
            case _ =>
          }
        case _ =>
          result += ImplicitResolveResult(retTp, r.element, r.importsUsed, r.substitutor, implicitDepSusbt, isFromCompanion, Seq.empty)
      }
    }

    result.toSeq
  }

  @CachedMappedWithRecursionGuard(place, ArrayBuffer.empty, ModCount.getBlockModificationCount)
  private def buildSimpleImplicitMap(fromUnder: Boolean, exprType: Option[ScType] = None): ArrayBuffer[ImplicitMapResult] = {
    ScalaPsiUtil.debug(s"Simple implicit map: $exprType", LOG)

    val typez: ScType = exprType.getOrElse(placeType(fromUnder).getOrElse(return ArrayBuffer.empty))

    val processor = new CollectImplicitsProcessor(false)

    // Collect implicit conversions from bottom to up
    def treeWalkUp(p: PsiElement, lastParent: PsiElement) {
      if (p == null) return
      if (!p.processDeclarations(processor,
        ResolveState.initial,
        lastParent, place)) return
      p match {
        case (_: ScTemplateBody | _: ScExtendsBlock) => //template body and inherited members are at the same level
        case _ => if (!processor.changedLevel) return
      }
      treeWalkUp(p.getContext, p)
    }
    treeWalkUp(place, null)

    val result = new ArrayBuffer[ImplicitMapResult]
    if (typez == types.Nothing) return result
    if (typez.isInstanceOf[ScUndefinedType]) return result

    val sigsFound = processor.candidatesS.map(forMap(_, typez))


    for (res <- sigsFound if res.condition) {
      result += res
    }

    result
  }

  def forMap(r: ScalaResolveResult, typez: ScType): ImplicitMapResult = {
    ScalaPsiUtil.debug(s"Check implicit: $r for type: $typez", LOG)

    val default = ImplicitMapResult(condition = false, r, null, null, null, null, null)
    if (!PsiTreeUtil.isContextAncestor(ScalaPsiUtil.nameContext(r.element), place, false)) { //to prevent infinite recursion
      ProgressManager.checkCanceled()

      lazy val funType = Option(
        ScalaPsiManager.instance(place.getProject).getCachedClass(
          "scala.Function1", place.getResolveScope, ScalaPsiManager.ClassCategory.TYPE
        )
      ) collect {
        case cl: ScTrait => ScParameterizedType(ScalaType.designator(cl), cl.typeParameters.map(tp =>
          new ScUndefinedType(new ScTypeParameterType(tp, ScSubstitutor.empty), 1)))
      } flatMap {
        case p: ScParameterizedType => Some(p)
        case _ => None
      }

      def firstArgType = funType.map(_.typeArgs.head)

      def secondArgType = funType.map(_.typeArgs.apply(1))

      val subst = r.substitutor
      val (tp: ScType, retTp: ScType) = r.element match {
        case f: ScFunction if f.paramClauses.clauses.nonEmpty =>
          val params = f.paramClauses.clauses.head.parameters
          (subst.subst(params.head.getType(TypingContext.empty).getOrNothing),
           subst.subst(f.returnType.getOrNothing))
        case f: ScFunction =>
          subst.subst(f.returnType.getOrElse(return default)).conforms(funType.getOrElse(return default), new ScUndefinedSubstitutor())
            ._2.getSubstitutor match {
            case Some(innerSubst) => (innerSubst.subst(firstArgType.getOrElse(return default)), innerSubst.subst(secondArgType.getOrElse(return default)))
            case _ => (types.Nothing, types.Nothing)
          }
        case b: ScBindingPattern =>
          subst.subst(b.getType(TypingContext.empty).getOrElse(return default)).conforms(funType.getOrElse(return default), new ScUndefinedSubstitutor())
            ._2.getSubstitutor match {
            case Some(innerSubst) => (innerSubst.subst(firstArgType.getOrElse(return default)), innerSubst.subst(secondArgType.getOrElse(return default)))
            case _ => (types.Nothing, types.Nothing)
          }
        case param: ScParameter =>
          // View Bounds and Context Bounds are processed as parameters.
          subst.subst(param.getType(TypingContext.empty).getOrElse(return default)).conforms(funType.getOrElse(return default), new ScUndefinedSubstitutor())
            ._2.getSubstitutor match {
            case Some(innerSubst) => (innerSubst.subst(firstArgType.getOrElse(return default)), innerSubst.subst(secondArgType.getOrElse(return default)))
            case _ => (types.Nothing, types.Nothing)
          }
        case obj: ScObject =>
          subst.subst(obj.getType(TypingContext.empty).getOrElse(return default)).conforms(funType.getOrElse(return default), new ScUndefinedSubstitutor())
            ._2.getSubstitutor match {
            case Some(innerSubst) => (innerSubst.subst(firstArgType.getOrElse(return default)), innerSubst.subst(secondArgType.getOrElse(return default)))
            case _ => (types.Nothing, types.Nothing)
          }
      }
      val newSubst = r.element match {
        case f: ScFunction => ScalaPsiUtil.inferMethodTypesArgs(f, r.substitutor)
        case _ => ScSubstitutor.empty
      }
      if (!typez.weakConforms(newSubst.subst(tp))) {
        ScalaPsiUtil.debug(s"Implicit $r doesn't conform to $typez", LOG)

        ImplicitMapResult(condition = false, r, tp, retTp, null, null, null)
      } else {
        r.element match {
          case f: ScFunction if f.hasTypeParameters =>
            var uSubst = typez.conforms(newSubst.subst(tp), new ScUndefinedSubstitutor())._2
            uSubst.getSubstitutor(notNonable = false) match {
              case Some(unSubst) =>
                def hasRecursiveTypeParameters(typez: ScType): Boolean = {

                  var hasRecursiveTypeParameters = false
                  typez.recursiveUpdate {
                    case tpt: ScTypeParameterType =>
                      f.typeParameters.find(tp => (tp.name, ScalaPsiUtil.getPsiElementId(tp)) == (tpt.name, tpt.getId)) match {
                        case None => (true, tpt)
                        case _ =>
                          hasRecursiveTypeParameters = true
                          (true, tpt)
                      }
                    case tp: ScType => (hasRecursiveTypeParameters, tp)
                  }
                  hasRecursiveTypeParameters
                }
                for (tParam <- f.typeParameters) {
                  val lowerType: ScType = tParam.lowerBound.getOrNothing
                  if (lowerType != types.Nothing) {
                    val substedLower = unSubst.subst(subst.subst(lowerType))
                    if (!hasRecursiveTypeParameters(substedLower)) {
                      uSubst = uSubst.addLower((tParam.name, ScalaPsiUtil.getPsiElementId(tParam)), substedLower, additional = true)
                    }
                  }
                  val upperType: ScType = tParam.upperBound.getOrAny
                  if (upperType != types.Any) {
                    val substedUpper = unSubst.subst(subst.subst(upperType))
                    if (!hasRecursiveTypeParameters(substedUpper)) {
                      uSubst = uSubst.addUpper((tParam.name, ScalaPsiUtil.getPsiElementId(tParam)), substedUpper, additional = true)
                    }
                  }
                }

                uSubst.getSubstitutor(notNonable = false) match {
                  case Some(unSubst) =>
                    //let's update dependent method types
                    //todo: currently it looks like a hack in the right place, probably whole this class should be
                    //todo: rewritten in more clean and clear way.
                    val dependentSubst = new ScSubstitutor(() => {
                      val level = place.scalaLanguageLevelOrDefault
                      if (level >= Scala_2_10) {
                        f.paramClauses.clauses.headOption.map(_.parameters).toSeq.flatten.map {
                          case (param: ScParameter) => (new Parameter(param), typez)
                        }.toMap
                      } else Map.empty
                    })

                    def probablyHasDepententMethodTypes: Boolean = {
                      if (f.paramClauses.clauses.length != 2 || !f.paramClauses.clauses.last.isImplicit) return false
                      val implicitClauseParameters = f.paramClauses.clauses.last.parameters
                      var res = false
                      f.returnType.foreach(_.recursiveUpdate {
                        case rtTp if res => (true, rtTp)
                        case ScDesignatorType(p: ScParameter) if implicitClauseParameters.contains(p) =>
                          res = true
                          (true, tp)
                        case tp: ScType => (false, tp)
                      })
                      res
                    }

                    val implicitDependentSubst = new ScSubstitutor(() => {
                      val level = place.scalaLanguageLevelOrDefault
                      if (level >= Scala_2_10) {
                        if (probablyHasDepententMethodTypes) {
                          val params: Seq[Parameter] = f.paramClauses.clauses.last.effectiveParameters.map(
                            param => new Parameter(param))
                          val (inferredParams, expr, _) = InferUtil.findImplicits(params, None,
                            place, check = false, abstractSubstitutor = subst followed dependentSubst followed unSubst)
                          inferredParams.zip(expr).map {
                            case (param: Parameter, expr: Expression) =>
                              (param, expr.getTypeAfterImplicitConversion(checkImplicits = true, isShape = false, None)._1.get)
                          }.toMap
                        } else Map.empty
                      } else Map.empty
                    })


                    //todo: pass implicit parameters
                    ScalaPsiUtil.debug(s"Implicit $r is ok for type $typez", LOG)
                    ImplicitMapResult(condition = true, r, tp, dependentSubst.subst(retTp), newSubst, uSubst, implicitDependentSubst)
                  case _ =>
                    ScalaPsiUtil.debug(s"Implicit $r has problems with type parameters bounds for type $typez", LOG)
                    ImplicitMapResult(condition = false, r, tp, retTp, null, null, null)
                }
              case _ =>
                ScalaPsiUtil.debug(s"Implicit $r has problems with type parameters bounds for type $typez", LOG)
                ImplicitMapResult(condition = false, r, tp, retTp, null, null, null)
            }
          case _ =>
            ScalaPsiUtil.debug(s"Implicit $r is ok for type $typez", LOG)
            ImplicitMapResult(condition = true, r, tp, retTp, newSubst, null: ScUndefinedSubstitutor, ScSubstitutor.empty)
        }
      } //possible true
    } else default
  }


  class CollectImplicitsProcessor(withoutPrecedence: Boolean)(implicit override val typeSystem: TypeSystem)
    extends ImplicitProcessor(StdKinds.refExprLastRef, withoutPrecedence) {
    //can be null (in Unit tests or without library)
    private val funType: ScType = {
      val funClass: PsiClass = ScalaPsiManager.instance(place.getProject).getCachedClass(place.getResolveScope, "scala.Function1").orNull
      funClass match {
        case cl: ScTrait => ScParameterizedType(ScalaType.designator(funClass), cl.typeParameters.map(tp =>
          new ScUndefinedType(new ScTypeParameterType(tp, ScSubstitutor.empty))))
        case _ => null
      }
    }

    protected def getPlace: PsiElement = place

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
            if (!ScImplicitlyConvertible.checkFucntionIsEligible(f, place) ||
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
                    case tp@ScTypeParameterType(name, _, _, _, _) if typeParameters.contains(name) =>
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

  case class Implicit(tp: ScType, fun: ScTypedDefinition, importsUsed: Set[ImportUsed])

  val IMPLICIT_REFERENCE_NAME = "implicitReferenceName"
  val IMPLICIT_EXPRESSION_NAME = "implicitExpressionName"
  val IMPLICIT_CALL_TEXT = IMPLICIT_REFERENCE_NAME + "(" + IMPLICIT_EXPRESSION_NAME + ")"

  val FAKE_RESOLVE_RESULT_KEY: Key[ScalaResolveResult] = Key.create("fake.resolve.result.key")
  val FAKE_EXPRESSION_TYPE_KEY: Key[ScType] = Key.create("fake.expr.type.key")
  val FAKE_EXPECTED_TYPE_KEY: Key[Option[ScType]] = Key.create("fake.expected.type.key")

  def setupFakeCall(expr: ScMethodCall, rr: ScalaResolveResult, tp: ScType, expected: Option[ScType]) {
    expr.getInvokedExpr.putUserData(FAKE_RESOLVE_RESULT_KEY, rr)
    expr.args.exprs.head.putUserData(FAKE_EXPRESSION_TYPE_KEY, tp)
    expr.putUserData(FAKE_EXPECTED_TYPE_KEY, expected)
  }

  def checkFucntionIsEligible(function: ScFunction, place: PsiElement): Boolean = {
    if (!function.hasExplicitType) {
      if (PsiTreeUtil.isContextAncestor(function.getContainingFile, place, false)) {
        val commonContext = PsiTreeUtil.findCommonContext(function, place)
        if (place == commonContext) return true //weird case, it covers situation, when function comes from object, not treeWalkUp
        if (function == commonContext) return false
        else {
          var functionContext: PsiElement = function
          while (functionContext.getContext != commonContext) functionContext = functionContext.getContext
          var placeContext: PsiElement = place
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

  case class ImplicitMapResult(condition: Boolean, resolveResult: ScalaResolveResult, tp: ScType, rt: ScType,
                               newSubst: ScSubstitutor, subst: ScUndefinedSubstitutor,
                               implicitDependentSubst: ScSubstitutor)

  case class ImplicitResolveResult(tp: ScType, element: PsiNamedElement, importUsed: Set[ImportUsed],
                                   subst: ScSubstitutor, implicitDependentSubst: ScSubstitutor, isFromCompanion: Boolean,
                                   unresolvedTypeParameters: Seq[TypeParameter]) {
    def getClazz: Option[PsiClass] = {
      element.getParent match {
        case tb: ScTemplateBody => Some(PsiTreeUtil.getParentOfType(tb, classOf[PsiClass]))
        case _ => None
      }
    }

    def getTypeWithDependentSubstitutor: ScType = implicitDependentSubst.subst(tp)
  }
}
