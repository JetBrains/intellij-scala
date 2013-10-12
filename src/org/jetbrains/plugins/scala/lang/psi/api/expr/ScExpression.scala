package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiManager, ScalaPsiElementFactory}
import types.result.{TypingContext, TypeResult}
import toplevel.imports.usages.ImportUsed
import lang.resolve.{StdKinds, ScalaResolveResult}
import implicits.ScImplicitlyConvertible
import collection.mutable.ArrayBuffer
import types._
import collection.{Set, Seq}
import lang.resolve.processor.MethodResolveProcessor
import com.intellij.openapi.progress.ProgressManager
import nonvalue.Parameter
import nonvalue.ScMethodType
import nonvalue.ScTypePolymorphicType
import psi.ScalaPsiUtil
import base.ScLiteral
import lexer.ScalaTokenTypes
import result.Failure
import result.Success
import statements.ScTypeAliasDefinition
import com.intellij.psi._
import java.lang.Integer
import base.types.ScTypeElement
import com.intellij.psi.util.PsiModificationTracker
import caches.CachesUtil
import psi.ScalaPsiUtil.SafeCheckException
import extensions.ElementText
import scala.Some
import types.ScFunctionType
import lang.resolve.processor.MostSpecificUtil
import types.Conformance.AliasType
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitResolveResult

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with PsiAnnotationMemberValue {
  import ScExpression._
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
    
    ScalaPsiManager.getMappedWithRecursionPreventingWithRollback(this, data, ScalaPsiManager.TYPE_AFTER_IMPLICIT_KEY,
      (expr: ScExpression, data: Data) => {
        val (checkImplicits: Boolean, isShape: Boolean,
        expectedOption: Option[ScType],
        ignoreBaseTypes: Boolean,
        fromUnderscore: Boolean) = data

        if (isShape) return ExpressionTypeResult(Success(getShape()._1, Some(this)), Set.empty, None)
        val expected: ScType = expectedOption.getOrElse(expectedType(fromUnderscore).getOrElse(null))
        if (expected == null)
          return ExpressionTypeResult(getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes, fromUnderscore), Set.empty, None)

        val tr = getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
        def defaultResult: ExpressionTypeResult = ExpressionTypeResult(tr, Set.empty, None)
        if (!checkImplicits) return defaultResult //do not try implicit conversions for shape check

        val tp = tr match {
          case Success(innerTp, _) => innerTp
          case _ => return defaultResult
        }
        //if this result is ok, we do not need to think about implicits
        if (tp.conforms(expected)) return defaultResult

        //this functionality for checking if this expression can be implicitly changed and then
        //it will conform to expected type
        val convertible: ScImplicitlyConvertible = new ScImplicitlyConvertible(this)
        val firstPart = convertible.implicitMapFirstPart(Some(expected), fromUnderscore)
        var f: Seq[ImplicitResolveResult] =
          firstPart.filter(_.tp.conforms(expected))
        if (f.length == 0) {
          f = convertible.implicitMapSecondPart(Some(expected), fromUnderscore).filter(_.tp.conforms(expected))
        }
        if (f.length == 1) ExpressionTypeResult(Success(f(0).getTypeWithDependentSubstitutor, Some(this)), f(0).importUsed, Some(f(0).element))
        else if (f.length == 0) defaultResult
        else {
          val res = MostSpecificUtil(this, 1).mostSpecificForImplicit(f.toSet) match {
            case Some(innerRes) => innerRes
            case None => return defaultResult
          }
          ExpressionTypeResult(Success(res.getTypeWithDependentSubstitutor, Some(this)), res.importUsed, Some(res.element))
        }
      }, ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion", Some(this)), Set.empty, None), isOutOfCodeBlock = false)
  }

  def getTypeWithoutImplicits(ctx: TypingContext, //todo: remove TypingContext?
                              ignoreBaseTypes: Boolean = false, 
                              fromUnderscore: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    
    type Data = (Boolean, Boolean)
    val data = (ignoreBaseTypes, fromUnderscore)

    ScalaPsiManager.getMappedWithRecursionPreventingWithRollback(this, data, ScalaPsiManager.TYPE_WITHOUT_IMPLICITS,
      (expr: ScExpression, data: Data) => {
        val (ignoreBaseTypes: Boolean,
        fromUnderscore: Boolean) = data

        val inner = getNonValueType(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
        var res: ScType = inner match {
          case Success(r, _) => r
          case _ => return inner
        }

        def tryUpdateRes(checkExpectedType: Boolean) {
          if (checkExpectedType) {
            InferUtil.updateAccordingToExpectedType(Success(res, Some(this)), fromImplicitParameters = true,
              expectedType = expectedType(fromUnderscore), expr = this, check = checkExpectedType) match {
              case Success(newRes, _) => res = newRes
              case _ =>
            }
          }

          val checkImplicitParameters = ScalaPsiUtil.withEtaExpansion(this)
          if (checkImplicitParameters) {
            val tuple = InferUtil.updateTypeWithImplicitParameters(res, this, None, checkExpectedType)
            res = tuple._1
            implicitParameters = tuple._2
          }
        }

        def isMethodInvocation(expr: ScExpression = this): Boolean = {
          expr match {
            case p: ScPrefixExpr => false
            case p: ScPostfixExpr => false
            case _: MethodInvocation => true
            case p: ScParenthesisedExpr =>
              p.expr match {
                case Some(expr) => isMethodInvocation(expr)
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
              case Some(expected) => {
                expected match {
                  case ScFunctionType(_, params) =>
                  case p: ScParameterizedType if p.getFunctionType != None =>
                  case _ => {
                    expected.isAliasType match {
                      case Some(AliasType(ta: ScTypeAliasDefinition, _, _)) => {
                        ta.aliasedType match {
                          case Success(ScFunctionType(_, _), _) =>
                          case Success(p: ScParameterizedType, _) if p.getFunctionType != None =>
                          case _ => res = updateType(retType)
                        }
                      }
                      case _ => res = updateType(retType)
                    }
                  }
                }
              }
              case _ => res = updateType(retType)
            }
          }

          updateRes(expectedType(fromUnderscore))
        }

        res match {
          case ScTypePolymorphicType(ScMethodType(retType, params, _), tp) if params.length == 0  &&
                  !this.isInstanceOf[ScUnderscoreSection] =>
            removeMethodType(retType, t => ScTypePolymorphicType(t, tp))
          case ScMethodType(retType, params, _) if params.length == 0 &&
                  !this.isInstanceOf[ScUnderscoreSection] =>
            removeMethodType(retType)
          case _ =>
        }

        val valType = res.inferValueType.unpackedType

        if (ignoreBaseTypes) return Success(valType, Some(this))

        expectedType(fromUnderscore) match {
          case Some(expected) => {
            //value discarding
            if (expected.removeAbstracts == Unit) return Success(Unit, Some(this))
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
            if (needsNarrowing) {
              try {
                lazy val i = this match {
                  case l: ScLiteral => l.getValue match {
                    case i: Integer => i.intValue
                    case _ => scala.Int.MaxValue
                  }
                  case p: ScPrefixExpr =>
                    val mult = if (p.operation.getText == "-") -1 else 1
                    p.operand match {
                      case l: ScLiteral => l.getValue match {
                        case i: Integer => mult * i.intValue
                        case _ => scala.Int.MaxValue
                      }
                    }
                }
                expected match {
                  case types.Char => {
                    if (i >= scala.Char.MinValue.toInt && i <= scala.Char.MaxValue.toInt) {
                      return Success(Char, Some(this))
                    }
                  }
                  case types.Byte => {
                    if (i >= scala.Byte.MinValue.toInt && i <= scala.Byte.MaxValue.toInt) {
                      return Success(Byte, Some(this))
                    }
                  }
                  case types.Short => {
                    if (i >= scala.Short.MinValue.toInt && i <= scala.Short.MaxValue.toInt) {
                      return Success(Short, Some(this))
                    }
                  }
                  case _ =>
                }
              }
              catch {
                case _: NumberFormatException => //do nothing
              }
            }

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
                case Some(x) => return x
                case _ =>
              }
              case _ =>
            }
          }
          case _ =>
        }
        Success(valType, Some(this))
      }, Failure("Recursive getTypeWithoutImplicits", Some(this)), isOutOfCodeBlock = false)
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
      case expr: ScExpression => {
        ScalaPsiUtil.isAnonymousExpression(expr) match {
          case (-1, _) => (Nothing, "")
          case (i, expr: ScFunctionExpr) =>
            (new ScFunctionType(expr.result.map(_.getShape(ignoreAssign = true)._1).getOrElse(Nothing), Seq.fill(i)(Any))(getProject, getResolveScope), "")
          case (i, _) => (new ScFunctionType(Nothing, Seq.fill(i)(Any))(getProject, getResolveScope), "")
        }
      }
      case _ => (Nothing, "")
    }
  }

  @volatile
  protected var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  /**
   * Warning! There is a hack in scala compiler for ClassManifest and ClassTag.
   * In case of implicit parameter with type ClassManifest[T]
   * this method will return ClassManifest with substitutor of type T.
   * @return implicit parameters used for this expression
   */
  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()
    getType(TypingContext.empty) //to update implicitParameters field
    implicitParameters
  }

  def getNonValueType(ctx: TypingContext = TypingContext.empty, //todo: remove?
                      ignoreBaseType: Boolean = false, 
                      fromUnderscore: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    type Data = (Boolean, Boolean)
    val data = (ignoreBaseType, fromUnderscore)

    ScalaPsiManager.getMappedWithRecursionPreventingWithRollback(this, data, ScalaPsiManager.NON_VALUE_TYPE_KEY,
      (expr: ScExpression, data: Data) => {
      val (ignoreBaseType, fromUnderscore) = data

      if (fromUnderscore) return innerType(TypingContext.empty)

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
    }, Failure("Recursive getNonValueType", Some(this)), isOutOfCodeBlock = false)
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
    ScalaPsiManager.getMappedWithRecursionPreventingWithRollback(this, fromUnderscore, ScalaPsiManager.EXPECTED_TYPES_KEY,
      (expr: ScExpression, data: Boolean) => ExpectedTypes.expectedExprTypes(expr, fromUnderscore = data),
      Array.empty[(ScType, Option[ScTypeElement])], isOutOfCodeBlock = false)
  }

  def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = {
    ScalaPsiManager.getMappedWithRecursionPreventingWithRollback(this, fromUnderscore, ScalaPsiManager.SMART_EXPECTED_TYPE,
      (expr: ScExpression, data: Boolean) => ExpectedTypes.smartExpectedType(expr, fromUnderscore = data),
      None, isOutOfCodeBlock = false)
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
      case ref: ScReferenceExpression => {
        val resolve = ref.multiResolve(false)
        if (resolve.length == 1) {
          resolve.apply(0).asInstanceOf[ScalaResolveResult].implicitFunction
        } else None
      }
      case inf: ScInfixExpr if (inf.isLeftAssoc && this == inf.rOp) || (!inf.isLeftAssoc && this == inf.lOp) => {
        val resolve = inf.operation.multiResolve(false)
        if (resolve.length == 1) {
          resolve.apply(0).asInstanceOf[ScalaResolveResult].implicitFunction
        } else None
      }
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

  final def calculateReturns: Seq[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]
    def calculateReturns0(el: PsiElement) {
      el match {
        case tr: ScTryStmt => {
          calculateReturns0(tr.tryBlock)
          tr.catchBlock match {
            case Some(cBlock) => cBlock.expression.foreach(calculateReturns0)
            case None =>
          }
        }
        case block: ScBlock => {
          block.lastExpr match {
            case Some(expr) => calculateReturns0(expr)
            case _ => res += block
          }
        }
        case pe: ScParenthesisedExpr =>
          pe.expr.foreach(calculateReturns0)
        case m: ScMatchStmt => {
          m.getBranches.foreach(calculateReturns0)
        }
        case i: ScIfStmt => {
          i.elseBranch match {
            case Some(e) => {
              calculateReturns0(e)
              i.thenBranch match {
                case Some(then) => calculateReturns0(then)
                case _ =>
              }
            }
            case _ => res += i
          }
        }
        case infix @ ScInfixExpr(left @ ScExpression.Type(types.Boolean), ElementText(op), right @ ScExpression.Type(types.Boolean))
          if (op == "&&" || op == "||") =>
          calculateReturns0(left)
          calculateReturns0(right)

        //TODO "!contains" is a quick fix, function needs unit testing to validate its behavior
        case _ => if (!res.contains(el)) res += el
      }
    }
    calculateReturns0(this)
    res
  }

  def applyShapeResolveForExpectedType(tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation],
                                       tr: TypeResult[ScType]): Array[ScalaResolveResult] = {
    def inner(expr: ScExpression, tp: ScType, exprs: Seq[ScExpression], call: Option[MethodInvocation],
              tr: TypeResult[ScType]): Array[ScalaResolveResult] = {
      val applyProc =
        new MethodResolveProcessor(expr, "apply", List(exprs), Seq.empty, Seq.empty /* todo: ? */,
          StdKinds.methodsOnly, isShapeResolve = true)
      applyProc.processType(tp, expr)
      var cand = applyProc.candidates
      if (cand.length == 0 && call != None && !tr.isEmpty) {
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
      cand
    }
    type Data = (ScType, Seq[ScExpression], Option[MethodInvocation], TypeResult[ScType])
    ScalaPsiManager.getMappedWithRecursionPreventingWithRollback[ScExpression, Data,
      Array[ScalaResolveResult]](this, (tp, exprs, call, tr),
      ScalaPsiManager.EXPRESSION_APPLY_SHAPE_RESOLVE_KEY,
      (expr: ScExpression, tuple: Data) => inner(expr, tuple._1, tuple._2, tuple._3, tuple._4),
      Array.empty[ScalaResolveResult], isOutOfCodeBlock = false)
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