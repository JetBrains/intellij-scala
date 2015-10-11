package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.ElementText
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{MethodValue, SafeCheckException}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.implicits.{ImplicitCollector, ScImplicitlyConvertible}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, Set}

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with PsiAnnotationMemberValue with ImplicitParametersOwner {
  import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression._
  /**
   * This method returns real type, after using implicit conversions.
   * Second parameter to return is used imports for this conversion.
   * @param expectedOption to which type we trying to convert
   * @param ignoreBaseTypes parameter to avoid value discarding, literal narrowing, widening
   *                        this parameter is useful for refactorings (introduce variable)
   */
  def getTypeAfterImplicitConversion(checkImplicits: Boolean = true, isShape: Boolean = false,
                                     expectedOption: Option[ScType] = None,
                                     ignoreBaseTypes: Boolean = false,
                                     fromUnderscore: Boolean = false): ExpressionTypeResult = {
    type Data = (Boolean, Boolean, Option[ScType], Boolean, Boolean)
    val data = (checkImplicits, isShape, expectedOption, ignoreBaseTypes, fromUnderscore)

    CachesUtil.getMappedWithRecursionPreventingWithRollback(this, data, CachesUtil.TYPE_AFTER_IMPLICIT_KEY,
      (expr: ScExpression, data: Data) => {
        val (checkImplicits: Boolean, isShape: Boolean,
        expectedOption: Option[ScType],
        ignoreBaseTypes: Boolean,
        fromUnderscore: Boolean) = data

        if (isShape) ExpressionTypeResult(Success(getShape()._1, Some(this)), Set.empty, None)
        else {
          val expected: ScType = expectedOption.getOrElse(expectedType(fromUnderscore).orNull)
          if (expected == null) {
            ExpressionTypeResult(getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes, fromUnderscore), Set.empty, None)
          } else {
            val tr = getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
            def defaultResult: ExpressionTypeResult = ExpressionTypeResult(tr, Set.empty, None)
            if (!checkImplicits) defaultResult //do not try implicit conversions for shape check
            else {
              tr match {
                //if this result is ok, we do not need to think about implicits
                case Success(tp, _) if tp.conforms(expected) => defaultResult
                case Success(tp, _) =>
                  def checkForSAM(): Option[ExpressionTypeResult] = {
                    if (ScalaPsiUtil.isSAMEnabled(this) && ScFunctionType.isFunctionType(tp)) {
                      ScalaPsiUtil.toSAMType(expected, getResolveScope) match {
                        case Some(methodType) if tp.conforms(methodType) =>
                          Some(ExpressionTypeResult(Success(expected, Some(this)), Set.empty, None))
                        case _ => None
                      }
                    } else None
                  }

                  val possibleSAMres = this match {
                    case MethodValue(_) => checkForSAM() //eta expansion happened
                    case ScFunctionExpr(_, _) if fromUnderscore => checkForSAM()
                    case _ if !fromUnderscore && ScalaPsiUtil.isAnonExpression(this) => checkForSAM()
                    case _ => None
                  }
                  possibleSAMres match {
                    case Some(r) => return r
                    case _ =>
                  }

                  val functionType = ScFunctionType(expected, Seq(tp))(getProject, getResolveScope)
                  val results = new ImplicitCollector(this, functionType, functionType, None,
                    isImplicitConversion = true, isExtensionConversion = false).collect()
                  if (results.length == 1) {
                    val res = results.head
                    val paramType = InferUtil.extractImplicitParameterType(res)
                    paramType match {
                      case ScFunctionType(rt, Seq(param)) =>
                        ExpressionTypeResult(Success(rt, Some(this)), res.importsUsed, Some(res.getElement))
                      case _ =>
                        ScalaPsiManager.instance(getProject).getCachedClass(
                          "scala.Function1", getResolveScope, ScalaPsiManager.ClassCategory.TYPE
                        ) match {
                          case function1: ScTrait =>
                            ScParameterizedType(ScType.designator(function1), function1.typeParameters.map(tp =>
                              new ScUndefinedType(new ScTypeParameterType(tp, ScSubstitutor.empty), 1))) match {
                              case funTp: ScParameterizedType =>
                                val secondArg = funTp.typeArgs(1)
                                Conformance.undefinedSubst(funTp, paramType).getSubstitutor match {
                                  case Some(subst) =>
                                    val rt = subst.subst(secondArg)
                                    if (rt.isInstanceOf[ScUndefinedType]) defaultResult
                                    else {
                                      ExpressionTypeResult(Success(rt, Some(this)), res.importsUsed, Some(res.getElement))
                                    }
                                  case None => defaultResult
                                }
                              case _ => defaultResult
                            }
                          case _ => defaultResult
                        }
                    }
                  } else defaultResult
                case _ => defaultResult
              }
            }
          }
        }
      }, ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion", Some(this)), Set.empty, None),
      PsiModificationTracker.MODIFICATION_COUNT)
  }

  def getTypeWithoutImplicits(ctx: TypingContext, //todo: remove TypingContext?
                              ignoreBaseTypes: Boolean = false,
                              fromUnderscore: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()

    type Data = (Boolean, Boolean)
    val data = (ignoreBaseTypes, fromUnderscore)

    CachesUtil.getMappedWithRecursionPreventingWithRollback(this, data, CachesUtil.TYPE_WITHOUT_IMPLICITS,
      (expr: ScExpression, data: Data) => {
        val (ignoreBaseTypes: Boolean,
        fromUnderscore: Boolean) = data

        val inner = getNonValueType(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
        inner match {
          case Success(rtp, _) =>
            var res = rtp

            def tryUpdateRes(checkExpectedType: Boolean) {
              if (checkExpectedType) {
                InferUtil.updateAccordingToExpectedType(Success(res, Some(this)), fromImplicitParameters = true,
                  filterTypeParams = false, expectedType = expectedType(fromUnderscore), expr = this,
                  check = checkExpectedType) match {
                  case Success(newRes, _) => res = newRes
                  case _ =>
                }
              }

              val checkImplicitParameters = ScalaPsiUtil.withEtaExpansion(this)
              if (checkImplicitParameters) {
                val tuple = InferUtil.updateTypeWithImplicitParameters(res, this, None, checkExpectedType, fullInfo = false)
                res = tuple._1
                if (fromUnderscore) implicitParametersFromUnder = tuple._2
                else implicitParameters = tuple._2
              }
            }

            @tailrec
            def isMethodInvocation(expr: ScExpression = this): Boolean = {
              expr match {
                case p: ScPrefixExpr => false
                case p: ScPostfixExpr => false
                case _: MethodInvocation => true
                case p: ScParenthesisedExpr =>
                  p.expr match {
                    case Some(exp) => isMethodInvocation(exp)
                    case _ => false
                  }
                case _ => false
              }
            }
            if (!isMethodInvocation()) { //it is not updated according to expected type, let's do it
              val oldRes = res
              try {
                tryUpdateRes(checkExpectedType = true)
              } catch {
                case _: SafeCheckException =>
                  res = oldRes
                  tryUpdateRes(checkExpectedType = false)
              }
            }

            def removeMethodType(retType: ScType, updateType: ScType => ScType = t => t) {
              def updateRes(exp: Option[ScType]) {

                exp match {
                  case Some(expected) =>
                    expected.removeAbstracts match {
                      case ScFunctionType(_, params) =>
                      case expected if ScalaPsiUtil.isSAMEnabled(this) =>
                        ScalaPsiUtil.toSAMType(expected, getResolveScope) match {
                          case Some(_) =>
                          case _ => res = updateType(retType)
                        }
                      case _ => res = updateType(retType)
                    }
                  case _ => res = updateType(retType)
                }
              }

              updateRes(expectedType(fromUnderscore))
            }

            res match {
              case ScTypePolymorphicType(ScMethodType(retType, params, _), tp) if params.length == 0  &&
                      !ScUnderScoreSectionUtil.isUnderscore(this) =>
                removeMethodType(retType, t => ScTypePolymorphicType(t, tp))
              case ScMethodType(retType, params, _) if params.length == 0 &&
                      !ScUnderScoreSectionUtil.isUnderscore(this) =>
                removeMethodType(retType)
              case _ =>
            }

            val valType = res.inferValueType.unpackedType

            if (ignoreBaseTypes) Success(valType, Some(this))
            else {
              expectedType(fromUnderscore) match {
                case Some(expected) =>
                  //value discarding
                  if (expected.removeAbstracts equiv Unit) return Success(Unit, Some(this))
                  //numeric literal narrowing
                  val needsNarrowing = this match {
                    case _: ScLiteral => getNode.getFirstChildNode.getElementType == ScalaTokenTypes.tINTEGER
                    case p: ScPrefixExpr => p.operand match {
                      case l: ScLiteral =>
                        l.getNode.getFirstChildNode.getElementType == ScalaTokenTypes.tINTEGER &&
                                Set("+", "-").contains(p.operation.getText)
                      case _ => false
                    }
                    case _ => false
                  }

                  def checkNarrowing: Option[TypeResult[ScType]] = {
                    try {
                      lazy val i = this match {
                        case l: ScLiteral    => l.getValue match {
                          case i: Integer => i.intValue
                          case _          => scala.Int.MaxValue
                        }
                        case p: ScPrefixExpr =>
                          val mult = if (p.operation.getText == "-") -1 else 1
                          p.operand match {
                            case l: ScLiteral => l.getValue match {
                              case i: Integer => mult * i.intValue
                              case _          => scala.Int.MaxValue
                            }
                          }
                      }
                      expected.removeAbstracts match {
                        case types.Char  =>
                          if (i >= scala.Char.MinValue.toInt && i <= scala.Char.MaxValue.toInt) {
                            return Some(Success(Char, Some(this)))
                          }
                        case types.Byte  =>
                          if (i >= scala.Byte.MinValue.toInt && i <= scala.Byte.MaxValue.toInt) {
                            return Some(Success(Byte, Some(this)))
                          }
                        case types.Short =>
                          if (i >= scala.Short.MinValue.toInt && i <= scala.Short.MaxValue.toInt) {
                            return Some(Success(Short, Some(this)))
                          }
                        case _           =>
                      }
                    }
                    catch {
                      case _: NumberFormatException => //do nothing
                    }
                    None
                  }

                  val check = if (needsNarrowing) checkNarrowing else None
                  if (check.isDefined) check.get
                  else {
                    //numeric widening
                    def checkWidening(l: ScType, r: ScType): Option[TypeResult[ScType]] = {
                      (l, r) match {
                        case (Byte, Short | Int | Long | Float | Double) => Some(Success(expected, Some(this)))
                        case (Short, Int | Long | Float | Double) => Some(Success(expected, Some(this)))
                        case (Char, Byte | Short | Int | Long | Float | Double) => Some(Success(expected, Some(this)))
                        case (Int, Long | Float | Double) => Some(Success(expected, Some(this)))
                        case (Long, Float | Double) => Some(Success(expected, Some(this)))
                        case (Float, Double) => Some(Success(expected, Some(this)))
                        case _ => None
                      }
                    }
                    (valType.getValType, expected.getValType) match {
                      case (Some(l), Some(r)) => checkWidening(l, r) match {
                        case Some(x) => x
                        case _ => Success(valType, Some(this))
                      }
                      case _ => Success(valType, Some(this))
                    }
                  }
                case _ => Success(valType, Some(this))
              }
            }
          case _ => inner
        }
      }, Failure("Recursive getTypeWithoutImplicits", Some(this)), PsiModificationTracker.MODIFICATION_COUNT)
  }

  def getType(ctx: TypingContext = TypingContext.empty): TypeResult[ScType] = {
    this match {
      case ref: ScReferenceExpression if ref.refName == ScImplicitlyConvertible.IMPLICIT_EXPRESSION_NAME =>
        val data = getUserData(ScImplicitlyConvertible.FAKE_EXPRESSION_TYPE_KEY)
        if (data != null) return Success(data, Some(this))
      case _ =>
    }
    getTypeAfterImplicitConversion().tr
  }
  def getTypeIgnoreBaseType(ctx: TypingContext = TypingContext.empty): TypeResult[ScType] = getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr
  def getTypeExt(ctx: TypingContext = TypingContext.empty): ScExpression.ExpressionTypeResult = getTypeAfterImplicitConversion()

  def getShape(ignoreAssign: Boolean = false): (ScType, String) = {
    this match {
      case assign: ScAssignStmt if !ignoreAssign && assign.assignName != None =>
        (assign.getRExpression.map(_.getShape(ignoreAssign = true)._1).getOrElse(Nothing), assign.assignName.get)
      case expr: ScExpression =>
        ScalaPsiUtil.isAnonymousExpression(expr) match {
          case (-1, _) => (Nothing, "")
          case (i, expr: ScFunctionExpr) =>
            (ScFunctionType(expr.result.map(_.getShape(ignoreAssign = true)._1).getOrElse(Nothing), Seq.fill(i)(Any))(getProject, getResolveScope), "")
          case (i, _) => (ScFunctionType(Nothing, Seq.fill(i)(Any))(getProject, getResolveScope), "")
        }
      case _ => (Nothing, "")
    }
  }

  @volatile
  protected var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  @volatile
  protected var implicitParametersFromUnder: Option[Seq[ScalaResolveResult]] = None

  /**
   * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
   * In case of implicit parameter with type ClassManifest[T]
   * this method will return ClassManifest with substitutor of type T.
   * @return implicit parameters used for this expression
   */
  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()

    if (ScUnderScoreSectionUtil.underscores(this).nonEmpty) {
      getTypeWithoutImplicits(TypingContext.empty, fromUnderscore = true) //to update implicitParametersFromUnder
      implicitParametersFromUnder
    } else {
      getType(TypingContext.empty) //to update implicitParameters field
      implicitParameters
    }
  }

  def getNonValueType(ctx: TypingContext = TypingContext.empty, //todo: remove?
                      ignoreBaseType: Boolean = false,
                      fromUnderscore: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    type Data = (Boolean, Boolean)
    val data = (ignoreBaseType, fromUnderscore)

    CachesUtil.getMappedWithRecursionPreventingWithRollback(this, data, CachesUtil.NON_VALUE_TYPE_KEY,
      (expr: ScExpression, data: Data) => {
      val (ignoreBaseType, fromUnderscore) = data

      if (fromUnderscore) innerType(TypingContext.empty)
      else {
        val unders = ScUnderScoreSectionUtil.underscores(this)
        if (unders.length == 0) innerType(TypingContext.empty)
        else {
          val params = unders.zipWithIndex.map {
            case (u, index) =>
              val tpe = u.getNonValueType(TypingContext.empty, ignoreBaseType).getOrAny.inferValueType.unpackedType
              new Parameter("", None, tpe, false, false, false, index)
          }
          val methType =
            new ScMethodType(getTypeAfterImplicitConversion(ignoreBaseTypes = ignoreBaseType,
              fromUnderscore = true).tr.getOrAny,
              params, false)(getProject, getResolveScope)
          new Success(methType, Some(this))
        }
      }
    }, Failure("Recursive getNonValueType", Some(this)), PsiModificationTracker.MODIFICATION_COUNT)
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] =
    Failure(ScalaBundle.message("no.type.inferred", getText), Some(this))

  /**
   * Some expression may be replaced only with another one
   */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, removeParenthesis = true)
    }
    val newExpr: ScExpression = if (ScalaPsiUtil.needParentheses(this, expr)) {
      ScalaPsiElementFactory.createExpressionFromText("(" + expr.getText + ")", getManager)
    } else expr
    val parentNode = oldParent.getNode
    val newNode = newExpr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    newNode.getPsi.asInstanceOf[ScExpression]
  }


  def expectedType(fromUnderscore: Boolean = true): Option[ScType] = {
    this match {
      case ref: ScMethodCall if ref.getText == ScImplicitlyConvertible.IMPLICIT_CALL_TEXT =>
        val data = getUserData(ScImplicitlyConvertible.FAKE_EXPECTED_TYPE_KEY)
        if (data != null) return data
      case _ =>
    }
    expectedTypeEx(fromUnderscore).map(_._1)
  }

  def expectedTypeEx(fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] =
    ExpectedTypes.expectedExprType(this, fromUnderscore)

  def expectedTypes(fromUnderscore: Boolean = true): Array[ScType] = expectedTypesEx(fromUnderscore).map(_._1)

  def expectedTypesEx(fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
    CachesUtil.getMappedWithRecursionPreventingWithRollback(this, fromUnderscore, CachesUtil.EXPECTED_TYPES_KEY,
      (expr: ScExpression, data: Boolean) => ExpectedTypes.expectedExprTypes(expr, fromUnderscore = data),
      Array.empty[(ScType, Option[ScTypeElement])], PsiModificationTracker.MODIFICATION_COUNT)
  }

  def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = {
    CachesUtil.getMappedWithRecursionPreventingWithRollback(this, fromUnderscore, CachesUtil.SMART_EXPECTED_TYPE,
      (expr: ScExpression, data: Boolean) => ExpectedTypes.smartExpectedType(expr, fromUnderscore = data),
      None, PsiModificationTracker.MODIFICATION_COUNT)
  }

  @volatile
  private var additionalExpression: Option[(ScExpression, ScType)] = None

  def setAdditionalExpression(additionalExpression: Option[(ScExpression, ScType)]) {
    this.additionalExpression = additionalExpression
  }

  /**
   * This method should be used to get implicit conversions and used imports, while eta expanded method return was
   * implicitly converted
   * @return mirror for this expression, in case if it exists
   */
  def getAdditionalExpression: Option[(ScExpression, ScType)] = {
    getType(TypingContext.empty)
    additionalExpression
  }

  /**
   * This method returns following values:
   * @return implicit conversions, actual value, conversions from the first part, conversions from the second part
   */
  def getImplicitConversions(fromUnder: Boolean = false,
                             expectedOption: => Option[ScType] = smartExpectedType()):
    (Seq[PsiNamedElement], Option[PsiNamedElement], Seq[PsiNamedElement], Seq[PsiNamedElement]) = {
    val map = new ScImplicitlyConvertible(this).implicitMap(fromUnder = fromUnder, args = expectedTypes(fromUnder).toSeq)
    val implicits: Seq[PsiNamedElement] = map.map(_.element)
    val implicitFunction: Option[PsiNamedElement] = getParent match {
      case ref: ScReferenceExpression =>
        val resolve = ref.multiResolve(false)
        if (resolve.length == 1) {
          resolve.apply(0).asInstanceOf[ScalaResolveResult].implicitFunction
        } else None
      case inf: ScInfixExpr if (inf.isLeftAssoc && this == inf.rOp) || (!inf.isLeftAssoc && this == inf.lOp) =>
        val resolve = inf.operation.multiResolve(false)
        if (resolve.length == 1) {
          resolve.apply(0).asInstanceOf[ScalaResolveResult].implicitFunction
        } else None
      case call: ScMethodCall => call.getImplicitFunction
      case gen: ScGenerator => gen.getParent match {
        case call: ScMethodCall => call.getImplicitFunction
        case _ => None
      }
      case _ => getTypeAfterImplicitConversion(expectedOption = expectedOption,
        fromUnderscore = fromUnder).implicitFunction
    }
    (implicits, implicitFunction, map.filter(!_.isFromCompanion).map(_.element), map.filter(_.isFromCompanion).map(_.element))
  }

  final def calculateReturns(withBooleanInfix: Boolean = false): Seq[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]
    def calculateReturns0(el: PsiElement) {
      el match {
        case tr: ScTryStmt =>
          calculateReturns0(tr.tryBlock)
          tr.catchBlock match {
            case Some(ScCatchBlock(caseCl)) =>
              caseCl.caseClauses.flatMap(_.expr).foreach(calculateReturns0)
            case _ =>
          }
        case block: ScBlock =>
          block.lastExpr match {
            case Some(expr) => calculateReturns0(expr)
            case _ => res += block
          }
        case pe: ScParenthesisedExpr =>
          pe.expr.foreach(calculateReturns0)
        case m: ScMatchStmt =>
          m.getBranches.foreach(calculateReturns0)
        case i: ScIfStmt =>
          i.elseBranch match {
            case Some(e) =>
              calculateReturns0(e)
              i.thenBranch match {
                case Some(thenBranch) => calculateReturns0(thenBranch)
                case _ =>
              }
            case _ => res += i
          }
        case infix @ ScInfixExpr(ScExpression.Type(types.Boolean), ElementText(op), right @ ScExpression.Type(types.Boolean))
          if withBooleanInfix && (op == "&&" || op == "||") => calculateReturns0(right)
        //TODO "!contains" is a quick fix, function needs unit testing to validate its behavior
        case _ => if (!res.contains(el)) res += el
      }
    }
    calculateReturns0(this)
    res
  }

  def applyShapeResolveForExpectedType(tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation]): Array[ScalaResolveResult] = {
    def inner(expr: ScExpression, tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation]): Array[ScalaResolveResult] = {
      val applyProc =
        new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */,
          StdKinds.methodsOnly, isShapeResolve = true)
      applyProc.processType(tp, expr)
      var cand = applyProc.candidates
      if (cand.length == 0 && call != None) {
        val expr = call.get.getEffectiveInvokedExpr
        ScalaPsiUtil.findImplicitConversion(expr, "apply", expr, applyProc, noImplicitsForArgs = false) match {
          case Some(res) =>
            var state = ResolveState.initial.put(CachesUtil.IMPLICIT_FUNCTION, res.element)
            res.getClazz match {
              case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
              case _ =>
            }
            applyProc.processType(res.getTypeWithDependentSubstitutor, expr, state)
            cand = applyProc.candidates
          case _ =>
        }
      }
      if (cand.length == 0 && ScalaPsiUtil.approveDynamic(tp, getProject, getResolveScope) && call.isDefined) {
        cand = ScalaPsiUtil.processTypeForUpdateOrApplyCandidates(call.get, tp, isShape = true, isDynamic = true)
      }
      cand
    }
    type Data = (ScType, Seq[ScExpression], Option[MethodInvocation])
    CachesUtil.getMappedWithRecursionPreventingWithRollback[ScExpression, Data,
      Array[ScalaResolveResult]](this, (tp, exprs, call),
      CachesUtil.EXPRESSION_APPLY_SHAPE_RESOLVE_KEY,
      (expr: ScExpression, tuple: Data) => inner(expr, tuple._1, tuple._2, tuple._3),
      Array.empty[ScalaResolveResult], PsiModificationTracker.MODIFICATION_COUNT)
  }
}

object ScExpression {
  case class ExpressionTypeResult(tr: TypeResult[ScType],
                                  importsUsed: scala.collection.Set[ImportUsed],
                                  implicitFunction: Option[PsiNamedElement])

  object Type {
    def unapply(exp: ScExpression): Option[ScType] = exp.getType(TypingContext.empty).toOption
  }
}