package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.base.ScStableCodeReferenceElement
import api.statements.{ScFunction, ScFun}
import api.toplevel.ScTypedDefinition
import api.toplevel.typedef.{ScClass, ScObject}
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction}
import api.base.types.ScTypeElement
import types._
import com.intellij.psi._
import nonvalue.{TypeParameter, Parameter, ScMethodType, ScTypePolymorphicType}
import result.{TypeResult, Failure, Success, TypingContext}
import implicits.ScImplicitlyConvertible
import api.toplevel.imports.usages.ImportUsed
import com.intellij.openapi.progress.ProgressManager
import lang.resolve.{ResolveUtils, ScalaResolveResult}
import api.statements.params.{ScTypeParam, ScParameters}
import types.Compatibility.Expression
import lang.resolve.processor.MethodResolveProcessor

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMethodCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMethodCall {
  override def toString: String = "MethodCall"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val inner: ScType = {
      def processType(tp: ScType): ScType = {
        val isUpdate = getContext.isInstanceOf[ScAssignStmt] && getContext.asInstanceOf[ScAssignStmt].getLExpression == this
        val methodName = if (isUpdate) "update" else "apply"
        val args: Seq[ScExpression] = this.args.exprs ++ (
                if (isUpdate) getContext.asInstanceOf[ScAssignStmt].getRExpression match {
                  case Some(x) => Seq[ScExpression](x)
                  case None =>
                    Seq[ScExpression](ScalaPsiElementFactory.createExpressionFromText("{val x: Nothing = null; x}",
                      getManager)) //we can't to not add something => add Nothing expression
                }
                else Seq.empty)
        val typeArgs: Seq[ScTypeElement] = getInvokedExpr match {
          case gen: ScGenericCall => gen.arguments
          case _ => Seq.empty
        }
        import Compatibility.Expression._
        val processor = new MethodResolveProcessor(getInvokedExpr, methodName, args :: Nil, typeArgs)
        processor.processType(tp, getInvokedExpr, ResolveState.initial)
        var candidates = processor.candidates
        if (candidates.length == 0) {
          //should think about implicit conversions
          for (t <- getInvokedExpr.getImplicitTypes) {
            ProgressManager.checkCanceled
            val importsUsed = getInvokedExpr.getImportsForImplicit(t)
            var state = ResolveState.initial.put(ImportUsed.key, importsUsed)
            getInvokedExpr.getClazzForType(t) match {
              case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
              case _ =>
            }
            processor.processType(t, getInvokedExpr, state)
          }
        }

        candidates = processor.candidates
        //now we will check canidate
        if (candidates.length != 1) Nothing
        else {
          candidates(0) match {
            case ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor) => {
              fun match {
                case fun: ScFun => s.subst(fun.polymorphicType)
                case fun: ScFunction => s.subst(fun.polymorphicType)
                case meth: PsiMethod => ResolveUtils.javaPolymorphicType(meth, s)
              }
            }
            case _ => Nothing
          }
        }
      }
      var nonValueType = getInvokedExpr.getNonValueType(TypingContext.empty)
      val res = nonValueType match {
        case Success(ScFunctionType(retType: ScType, params: Seq[ScType]), _) => retType
        case Success(ScMethodType(retType, _, _), _) => retType
        case Success(ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams), _) => {
          val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
          ScalaPsiUtil.localTypeInference(retType, params, exprs, typeParams)
        }
        case Success(tp: ScType, _) => processType(tp) match {
          case ScFunctionType(retType: ScType, params: Seq[ScType]) => retType
          case ScMethodType(retType, _, _) => retType
          case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) => {
            val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
            ScalaPsiUtil.localTypeInference(retType, params, exprs, typeParams)
          }
          case tp => tp
        }
        case x => return x
      }
      res
    }

    Success(inner, Some(this))
  }


}