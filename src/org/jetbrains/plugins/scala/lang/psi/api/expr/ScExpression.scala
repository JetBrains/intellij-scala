package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import impl.ScalaPsiElementFactory
import types.result.{Success, Failure, TypingContext, TypeResult}
import toplevel.imports.usages.ImportUsed
import resolve.ScalaResolveResult
import implicits.ScImplicitlyConvertible
import collection.mutable.ArrayBuffer
import types._
import nonvalue._
import collection.{Set, Seq}
import resolve.processor.MostSpecificUtil
import com.intellij.openapi.progress.ProgressManager
import psi.ScalaPsiUtil
import base.ScLiteral
import lexer.ScalaTokenTypes
import types.Conformance.AliasType
import statements.ScTypeAliasDefinition
import com.intellij.psi.{PsiAnnotationMemberValue, PsiNamedElement, PsiElement, PsiInvalidElementAccessException}
import java.lang.Integer
import base.types.ScTypeElement
import com.intellij.psi.util.PsiModificationTracker
import caches.CachesUtil
import psi.ScalaPsiUtil.SafeCheckException
/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with ScImplicitlyConvertible with PsiAnnotationMemberValue {
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
    
    def compute(expr: ScExpression, data: Data): ExpressionTypeResult = {
      val (checkImplicits: Boolean, isShape: Boolean,
        expectedOption: Option[ScType],
        ignoreBaseTypes: Boolean,
        fromUnderscore: Boolean) = data
      
      if (isShape) return ExpressionTypeResult(Success(getShape()._1, Some(this)), Set.empty, None)
      val expected: ScType = expectedOption.getOrElse(expectedType(fromUnderscore).getOrElse(null))
      if (expected == null) 
        return ExpressionTypeResult(getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes, fromUnderscore), Set.empty, None)
      
      val tr = getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
      lazy val defaultResult: ExpressionTypeResult = ExpressionTypeResult(tr, Set.empty, None)
      if (!checkImplicits) return defaultResult //do not try implicit conversions for shape check

      val tp = tr match {
        case Success(innerTp, _) => innerTp
        case _ => return defaultResult
      }
      //if this result is ok, we do not need to think about implicits
      if (tp.conforms(expected)) return defaultResult

      //this functionality for checking if this expression can be implicitly changed and then
      //it will conform to expected type
      var f: Seq[(ScType, PsiNamedElement, Set[ImportUsed])] =
        implicitMapFirstPart(Some(expected), fromUnderscore).filter(_._1.conforms(expected))
      if (f.length == 0) {
        f = implicitMapSecondPart(Some(expected), fromUnderscore).filter(_._1.conforms(expected))
      }
      if (f.length == 1) ExpressionTypeResult(Success(f(0)._1, Some(this)), f(0)._3, Some(f(0)._2))
      else if (f.length == 0) defaultResult
      else {
        val res = MostSpecificUtil(this, 1).mostSpecificForImplicit(f.toSet) match {
          case Some(innerRes) => innerRes
          case None => return defaultResult
        }
        ExpressionTypeResult(Success(res._1, Some(this)), res._3, Some(res._2))
      }
    }
    
    CachesUtil.getMappedWithRecursionPreventing(this, data, CachesUtil.TYPE_AFTER_IMPLICIT_KEY, compute,
      ExpressionTypeResult(Failure("Recursive getTypeAfterImplicitConversion", Some(this)), Set.empty, None),
      PsiModificationTracker.MODIFICATION_COUNT)
  }

  def getTypeWithoutImplicits(ctx: TypingContext, //todo: remove TypingContext?
                              ignoreBaseTypes: Boolean = false, 
                              fromUnderscore: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    
    type Data = (Boolean, Boolean)
    val data = (ignoreBaseTypes, fromUnderscore)

    def compute(expr: ScExpression, data: Data): TypeResult[ScType] = {
      val (ignoreBaseTypes: Boolean,
           fromUnderscore: Boolean) = data
      
      val inner = getNonValueType(TypingContext.empty, ignoreBaseTypes, fromUnderscore)
      var res: ScType = inner match {
        case Success(r, _) => r
        case _ => return inner
      }

      def tryUpdateRes(checkExpectedType: Boolean) {
        if (checkExpectedType) {
          InferUtil.updateAccordingToExpectedType(Success(res, Some(this)), true,
            expectedType(fromUnderscore), this, checkExpectedType) match {
            case Success(newRes, _) => res = newRes
            case _ =>
          }
        }

        val checkImplicitParameters = ScalaPsiUtil.withEtaExpansion(this)
        if (checkImplicitParameters) {
          val tuple = InferUtil.updateTypeWithImplicitParameters(res, this, checkExpectedType)
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
          tryUpdateRes(true)
        } catch {
          case _: SafeCheckException =>
            res = oldRes
            tryUpdateRes(false)
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
                  Conformance.isAliasType(expected) match {
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

      val valType = res.inferValueType

      if (ignoreBaseTypes) return Success(valType, Some(this))

      expectedType(fromUnderscore) match {
        case Some(expected) => {
          //value discarding
          if (expected == Unit) return Success(Unit, Some(this))
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
    }
    
    CachesUtil.getMappedWithRecursionPreventing(this, data, CachesUtil.TYPE_WITHOUT_IMPLICITS,
      compute, Failure("Recursive getTypeWithoutImplicits", Some(this)), PsiModificationTracker.MODIFICATION_COUNT)
  }

  def getType(ctx: TypingContext): TypeResult[ScType] = getTypeAfterImplicitConversion().tr
  def getTypeIgnoreBaseType(ctx: TypingContext): TypeResult[ScType] = getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr
  def getTypeExt(ctx: TypingContext): ScExpression.ExpressionTypeResult = getTypeAfterImplicitConversion()

  def getShape(ignoreAssign: Boolean = false): (ScType, String) = {
    this match {
      case assign: ScAssignStmt if !ignoreAssign && assign.assignName != None =>
        (assign.getRExpression.map(_.getShape(true)._1).getOrElse(Nothing), assign.assignName.get)
      case expr: ScExpression => {
        ScalaPsiUtil.isAnonymousExpression(expr) match {
          case (-1, _) => (Nothing, "")
          case (i, expr: ScFunctionExpr) =>
            (new ScFunctionType(expr.result.map(_.getShape(true)._1).getOrElse(Nothing), Seq.fill(i)(Any))(getProject, getResolveScope), "")
          case (i, _) => (new ScFunctionType(Nothing, Seq.fill(i)(Any))(getProject, getResolveScope), "")
        }
      }
      case _ => (Nothing, "")
    }
  }

  @volatile
  protected var implicitParameters: Option[Seq[ScalaResolveResult]] = None
  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()
    getType(TypingContext.empty) //to update implicitParameters field
    implicitParameters
  }

  def getNonValueType(ctx: TypingContext, //todo: remove?
                      ignoreBaseType: Boolean = false, 
                      fromUnderscore: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    type Data = (Boolean, Boolean)
    val data = (ignoreBaseType, fromUnderscore)
    
    def compute(expr: ScExpression, data: Data): TypeResult[ScType] = {
      val (ignoreBaseType, fromUnderscore) = data

      if (fromUnderscore) return innerType(TypingContext.empty)

      val unders = ScUnderScoreSectionUtil.underscores(this)
      if (unders.length == 0) innerType(TypingContext.empty)
      else {
        val params = unders.map {u =>
          new Parameter("", u.getNonValueType(TypingContext.empty,
            ignoreBaseType).getOrAny.inferValueType, false, false, false)
        }
        val methType =
          new ScMethodType(getTypeAfterImplicitConversion(ignoreBaseTypes = ignoreBaseType,
            fromUnderscore = true).tr.getOrAny,
            params, false)(getProject, getResolveScope)
        new Success(methType, Some(this))
      }
    }

    CachesUtil.getMappedWithRecursionPreventing(this, data, CachesUtil.NON_VALUE_TYPE_KEY,
      compute, Failure("Recursive getNonValueType", Some(this)), PsiModificationTracker.MODIFICATION_COUNT)
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] =
    Failure(ScalaBundle.message("no.type.inferred", getText), Some(this))

  /**
   * Returns all types in respect of implicit conversions (defined and default)
   */
  def allTypes: Seq[ScType] = {
    (getType(TypingContext.empty) match {
      case Success(t, _) => t :: getImplicitTypes
      case _ => getImplicitTypes
    })
  }

  def allTypesAndImports: List[(ScType, scala.collection.Set[ImportUsed])] = {
    def implicitTypesAndImports = {
      (for (t <- getImplicitTypes) yield (t, getImportsForImplicit(t)))
    }
    (getType(TypingContext.empty) match {
      case Success(t, _) => (t, Set[ImportUsed]()) :: implicitTypesAndImports
      case _ => implicitTypesAndImports
    })
  }

  /**
   * Some expression may be replaced only with another one
   */
  def replaceExpression(expr: ScExpression, removeParenthesis: Boolean): ScExpression = {
    val oldParent = getParent
    if (oldParent == null) throw new PsiInvalidElementAccessException(this)
    if (removeParenthesis && oldParent.isInstanceOf[ScParenthesisedExpr]) {
      return oldParent.asInstanceOf[ScExpression].replaceExpression(expr, true)
    }
    val newExpr: ScExpression = if (ScalaPsiUtil.needParentheses(this, expr)) {
      ScalaPsiElementFactory.createExpressionFromText("(" + expr.getText + ")", getManager)
    } else expr
    val parentNode = oldParent.getNode
    val newNode = newExpr.copy.getNode
    parentNode.replaceChild(this.getNode, newNode)
    newNode.getPsi.asInstanceOf[ScExpression]
  }


  def expectedType(fromUnderscore: Boolean = true): Option[ScType] = expectedTypeEx(fromUnderscore).map(_._1)

  def expectedTypeEx(fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])] =
    ExpectedTypes.expectedExprType(this, fromUnderscore)

  def expectedTypes(fromUnderscore: Boolean = true): Array[ScType] = expectedTypesEx(fromUnderscore).map(_._1)
  
  def expectedTypesEx(fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])] = {
    CachesUtil.getMappedWithRecursionPreventing(this, fromUnderscore, CachesUtil.EXPECTED_TYPES_KEY,
      (expr: ScExpression, data: Boolean) => ExpectedTypes.expectedExprTypes(expr, fromUnderscore = data),
      Array.empty[(ScType, Option[ScTypeElement])], PsiModificationTracker.MODIFICATION_COUNT)
  }

  def smartExpectedType(fromUnderscore: Boolean = true): Option[ScType] = {
    CachesUtil.getMappedWithRecursionPreventing(this, fromUnderscore, CachesUtil.SMART_EXPECTED_TYPE,
      (expr: ScExpression, data: Boolean) => ExpectedTypes.smartExpectedType(expr, fromUnderscore = data),
      None, PsiModificationTracker.MODIFICATION_COUNT)
  }

  def getImplicitConversions(fromUnder: Boolean = false): (Seq[PsiNamedElement], Option[PsiNamedElement]) = {
    val implicits: Seq[PsiNamedElement] = implicitMap(fromUnder = fromUnder,
      args = expectedTypes(fromUnder).toSeq).map(_._2)
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
      case _ => getTypeAfterImplicitConversion(expectedOption = smartExpectedType(),
        fromUnderscore = fromUnder).implicitFunction
    }
    (implicits, implicitFunction)
  }

  final def calculateReturns: Seq[PsiElement] = {
    val res = new ArrayBuffer[PsiElement]
    def calculateReturns0(el: PsiElement) {
      el match {
        case tr: ScTryStmt => {
          calculateReturns0(tr.tryBlock)
          tr.catchBlock match {
            case Some(cBlock) => cBlock.getBranches.foreach(calculateReturns0)
            case None =>
          }
        }
        case block: ScBlock => {
          block.lastExpr match {
            case Some(expr) => calculateReturns0(expr)
            case _ => res += block
          }
        }
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
        //TODO "!contains" is a quick fix, function needs unit testing to validate its behavior
        case _ => if (!res.contains(el)) res += el
      }
    }
    calculateReturns0(this)
    res
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