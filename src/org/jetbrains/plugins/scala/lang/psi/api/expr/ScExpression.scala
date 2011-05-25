package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import impl.ScalaPsiElementFactory
import types.result.{Success, Failure, TypingContext, TypeResult}
import toplevel.imports.usages.ImportUsed
import types.Compatibility.Expression
import base.patterns.ScBindingPattern
import resolve.ScalaResolveResult
import implicits.{ImplicitParametersCollector, ScImplicitlyConvertible}
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
import statements.{ScTypeAliasDefinition, ScFunction}
import com.intellij.psi.{PsiAnnotationMemberValue, PsiNamedElement, PsiElement, PsiInvalidElementAccessException}
import java.lang.Integer
import base.types.ScTypeElement
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

trait ScExpression extends ScBlockStatement with ScImplicitlyConvertible with PsiAnnotationMemberValue {
  import ScExpression._
  /**
   * This method returns real type, after using implicit conversions.
   * Second parameter to return is used imports for this conversion.
   * @param expectedOption to which type we tring to convert
   * @param ignoreBaseTypes parameter to avoid value discarding, literal narrowing, widening
   *                        this parameter is useful for refactorings (introduce variable)
   */
  def getTypeAfterImplicitConversion(checkImplicits: Boolean = true, isShape: Boolean = false, 
                                     expectedOption: Option[ScType] = None,
                                     ignoreBaseTypes: Boolean = false): ExpressionTypeResult = {
    def inner: ExpressionTypeResult = {
      if (isShape) return ExpressionTypeResult(Success(getShape()._1, Some(this)), Set.empty, None)
      val expected: ScType = expectedOption match {
        case Some(a) => a
        case _ => expectedType match {
          case Some(a) => a
          case _ =>
            return ExpressionTypeResult(getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes), Set.empty, None)
        }
      }
      val tr = getTypeWithoutImplicits(TypingContext.empty, ignoreBaseTypes)

      def tryTp(tr: TypeResult[ScType], expected: ScType, fromUnder: Boolean): ScExpression.ExpressionTypeResult = {
        val defaultResult: ExpressionTypeResult = ExpressionTypeResult(tr, Set.empty, None)
        if (!checkImplicits) return defaultResult //do not try implicit conversions for shape check

        val tp = tr match {
          case Success(tp, _) => tp
          case _ => return defaultResult
        }
        //if this result is ok, we do not need to think about implicits
        if (tp.conforms(expected)) return defaultResult

        //this functionality for checking if this expression can be implicitly changed and then
        //it will conform to expected type
        val mp = implicitMap(Some(expected), fromUnder)
        val f: Seq[(ScType, PsiNamedElement, Set[ImportUsed])] = mp.filter(_._1.conforms(expected))
        if (f.length == 1) ExpressionTypeResult(Success(f(0)._1, Some(this)), f(0)._3, Some(f(0)._2))
        else if (f.length == 0) defaultResult
        else {
          val res = MostSpecificUtil(this, 1).mostSpecificForImplicit(f.toSet) match {
            case Some(res) => res
            case None => return defaultResult
          }
          ExpressionTypeResult(Success(res._1, Some(this)), res._3, Some(res._2))
        }
      }
      getText.indexOf("_") match {
        case -1 => tryTp(tr, expected, false)
        case _ => {
          val unders = ScUnderScoreSectionUtil.underscores(this)
          if (unders.length == 0) tryTp(tr, expected, false)
          else {
            //here we should update implicits twice for result expression and for all expression
            val newTr = tr.map(tp => {
              tp match {
                case f@ScFunctionType(ret, args) =>
                  ScalaPsiUtil.extractReturnType(expected) match {
                    case Some(expRet) =>
                      new ScFunctionType(tryTp(Success(ret, Some(this)), expRet, true).tr.getOrElse(ret),
                        args)(f.getProject, f.getScope)
                    case _ => tp
                  }
                case _ => tp
              }
            })
            tryTp(newTr, expected, false)
          }
        }
      }

    }
    if (!checkImplicits || isShape || expectedOption != None || ignoreBaseTypes)
      return inner //no cache with strange parameters

    CachesUtil.get(this, CachesUtil.TYPE_AFTER_IMPLICIT_KEY,
      new CachesUtil.MyProvider(this, (expr: ScExpression) => inner)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  def getTypeWithoutImplicits(ctx: TypingContext, 
                              ignoreBaseTypes: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    if (ctx != TypingContext.empty || ignoreBaseTypes) return valueType(ctx, ignoreBaseTypes = ignoreBaseTypes)
    CachesUtil.get(this, CachesUtil.TYPE_WITHOUT_IMPLICITS,
      new CachesUtil.MyProvider(this, (expr: ScExpression) => valueType(ctx))
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  def getTypeWithoutImplicitsWithoutUnderscore(ctx: TypingContext, 
                                               ignoreBaseTypes: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    if (ctx != TypingContext.empty || ignoreBaseTypes) return valueType(ctx, true, ignoreBaseTypes)
    CachesUtil.get(this, CachesUtil.TYPE_WITHOUT_IMPLICITS_WITHOUT_UNDERSCORE,
      new CachesUtil.MyProvider(this, (expr: ScExpression) => valueType(ctx, true))
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  def getType(ctx: TypingContext) = getTypeAfterImplicitConversion().tr

  def getTypeIgnoreBaseType(ctx: TypingContext) = getTypeAfterImplicitConversion(ignoreBaseTypes = true).tr

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
  private var implicitParameters: Option[Seq[ScalaResolveResult]] = None

  private def typeWithUnderscore(ctx: TypingContext, ignoreBaseTypes: Boolean = false): TypeResult[ScType] = {
    getText.indexOf("_") match {
      case -1 => innerType(ctx) //optimization
      case _ => {
        val unders = ScUnderScoreSectionUtil.underscores(this)
        if (unders.length == 0) innerType(ctx)
        else {
          val params = unders.map {u => 
            Parameter("", u.getTypeWithoutImplicits(ctx, ignoreBaseTypes).getOrElse(Any), false, false, false)
          }
          val methType = 
            new ScMethodType(getTypeWithoutImplicitsWithoutUnderscore(ctx, ignoreBaseTypes).getOrElse(Any), 
              params, false)(getProject, getResolveScope)
          new Success(methType, Some(this))
        }
      }
    }
  }

  def findImplicitParameters: Option[Seq[ScalaResolveResult]] = {
    ProgressManager.checkCanceled()
    getType(TypingContext.empty) //to update implicitParameters field
    implicitParameters
  }

  private def valueType(ctx: TypingContext, fromUnderscoreSection: Boolean = false, 
                        ignoreBaseTypes: Boolean = false): TypeResult[ScType] = {
    val inner = if (!fromUnderscoreSection) getNonValueType(ctx) else innerType(ctx)
    var res = inner match {
      case Success(res, _) => res
      case _ => return inner
    }

    //let's update implicitParameters field
    res match {
      case t@ScTypePolymorphicType(ScMethodType(retType, params, impl), typeParams) if impl => {
        /*val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
          (subst: ScSubstitutor, tp: TypeParameter) =>
            subst.bindT((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
              new ScUndefinedType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))
        }*/
        val polymorphicSubst = t.polymorphicTypeSubstitutor
        //val existentialSubst = t.existentialTypeSubstitutor
        val abstractSubstitutor: ScSubstitutor = t.abstractTypeSubstitutor
        val exprs = new ArrayBuffer[Expression]
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next()
          val paramType = abstractSubstitutor.subst(param.paramType) //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(this, paramType)
          val results = collector.collect
          if (results.length == 1) {
            resolveResults += results(0)
            results(0) match {
              case ScalaResolveResult(patt: ScBindingPattern, subst) => {
                exprs += new Expression(polymorphicSubst subst subst.subst(patt.getType(TypingContext.empty).get))
              }
              case ScalaResolveResult(fun: ScFunction, subst) => {
                val funType = {
                  if (fun.parameters.length == 0 || fun.paramClauses.clauses.apply(0).isImplicit) {
                    subst.subst(fun.getType(TypingContext.empty).get) match {
                      case ScFunctionType(ret, _) => ret
                      case x => x
                    }
                  }
                  else subst.subst(fun.getType(TypingContext.empty).get)
                }
                exprs += new Expression(polymorphicSubst subst funType)
              }
            }
          } else {
            resolveResults += null
            exprs += new Expression(Any)
          }
        }
        implicitParameters = Some(resolveResults.toSeq)
        res = ScalaPsiUtil.localTypeInference(retType, params, exprs.toSeq, typeParams, polymorphicSubst)
      }
      case ScMethodType(retType, params, isImplicit) if isImplicit => {
        val resolveResults = new ArrayBuffer[ScalaResolveResult]
        val iterator = params.iterator
        while (iterator.hasNext) {
          val param = iterator.next()
          val paramType = param.paramType //we should do all of this with information known before
          val collector = new ImplicitParametersCollector(this, paramType)
          val results = collector.collect
          if (results.length == 1) {
            resolveResults += results(0)
          } else {
            resolveResults += null
          }
        }
        implicitParameters = Some(resolveResults.toSeq)
        res = retType
      }
      case _ =>
    }

    res match {
      case ScTypePolymorphicType(ScMethodType(retType, params, _), tp) if params.length == 0 => {
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
                        case _ => res = ScTypePolymorphicType(retType, tp)
                      }
                    }
                    case _ => res = ScTypePolymorphicType(retType, tp)
                  }
                }
              }
            }
            case _ => res = ScTypePolymorphicType(retType, tp)
          }
        }
        if (!fromUnderscoreSection) {
          updateRes(expectedType)
        } else {
          expectedType match {
            case Some(ScFunctionType(retType, _)) => updateRes(Some(retType)) //todo: another functions
            case _ => res = ScTypePolymorphicType(retType, tp)
          }
        }
      }
      case ScMethodType(retType, params, _) if params.length == 0 => {
        //todo: duplicate
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
                        case _ => res = retType
                      }
                    }
                    case _ => res = retType
                  }
                }
              }
            }
            case _ => res = retType
          }
        }
        if (!fromUnderscoreSection) {
          updateRes(expectedType)
        } else {
          expectedType match {
            case Some(ScFunctionType(retType, _)) => updateRes(Some(retType))  //todo: another functions
            case _ => res = retType
          }
        }
      }
      case _ =>
    }

    res match {
      case ScTypePolymorphicType(internal, typeParams) if expectedType != None => {
        def updateRes(expected: ScType) {
          res = ScalaPsiUtil.localTypeInference(internal, Seq(Parameter("", expected, false, false, false)),
              Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(typeParams).subst(internal.inferValueType))),
            typeParams, shouldUndefineParameters = false) //here should work in different way:
        }
        if (!fromUnderscoreSection) {
          updateRes(expectedType.get)
        } else {
          expectedType.get match {
            case ScFunctionType(retType, _) => updateRes(retType) //todo: another functions
            case _ => //do not update res, we haven't expected type
          }
        }

      }
      case _ =>
    }

    val valType = res.inferValueType

    if (ignoreBaseTypes) return Success(valType, Some(this))

    expectedType match {
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
        (valType, expected) match {
          case (Byte, Short | Int | Long | Float | Double) => return Success(expected, Some(this))
          case (Short, Int | Long | Float | Double) => return Success(expected, Some(this))
          case (Char, Byte | Short | Int | Long | Float | Double) => return Success(expected, Some(this))
          case (Int, Long | Float | Double) => return Success(expected, Some(this))
          case (Long, Float | Double) => return Success(expected, Some(this))
          case (Float, Double) => return Success(expected, Some(this))
          case _ =>
        }
      }
      case _ =>
    }
    Success(valType, Some(this))
  }

  def getNonValueType(ctx: TypingContext, ignoreBaseType: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    if (ctx != TypingContext.empty || ignoreBaseType) return typeWithUnderscore(ctx, ignoreBaseType)
    CachesUtil.get(this, CachesUtil.NON_VALUE_TYPE_KEY,
      new CachesUtil.MyProvider(this, (expr: ScExpression) => typeWithUnderscore(ctx))
      (PsiModificationTracker.MODIFICATION_COUNT))
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


  def expectedType: Option[ScType] = expectedTypeEx.map(_._1)

  def expectedTypeEx: Option[(ScType, Option[ScTypeElement])] = ExpectedTypes.expectedExprType(this)

  def expectedTypes: Array[ScType] = expectedTypesEx.map(_._1)
  
  def expectedTypesEx: Array[(ScType, Option[ScTypeElement])] = {
    CachesUtil.get(this, CachesUtil.EXPECTED_TYPES_KEY,
      new CachesUtil.MyProvider(this, (expr: ScExpression) => ExpectedTypes.expectedExprTypes(this))
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  def getImplicitConversions: (Seq[PsiNamedElement], Option[PsiElement]) = {
    val implicits: Seq[PsiNamedElement] = implicitMap().map(_._2)
    val implicitFunction: Option[PsiElement] = getParent match {
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
      case call: ScMethodCall => None //todo:
      case gen: ScGenerator => None //todo:
      case _ => getTypeAfterImplicitConversion(expectedOption = ExpectedTypes.smartExpectedType(this)).implicitFunction
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
                case Some(e) => calculateReturns0(e)
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