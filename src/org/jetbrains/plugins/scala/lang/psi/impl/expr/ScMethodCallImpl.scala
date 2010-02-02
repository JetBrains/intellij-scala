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
import lang.resolve.{MethodResolveProcessor, ScalaResolveResult}
import api.base.types.ScTypeElement
import types._
import com.intellij.psi._
import api.statements.params.ScParameters
import result.{TypeResult, Failure, Success, TypingContext}
import implicits.ScImplicitlyConvertible
import api.toplevel.imports.usages.ImportUsed
import com.intellij.openapi.progress.ProgressManager

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMethodCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMethodCall {
  override def toString: String = "MethodCall"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {

    // todo rewrite me!
    val inner: ScType = {
      /**
       * Utility method to get type for apply (and update) methods of concrecte class.
       */
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
        val processor = new MethodResolveProcessor(getInvokedExpr, methodName, args :: Nil,
          typeArgs, expectedType)
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
                case fun: ScFun => s.subst(fun.retType)
                case fun: ScFunction => s.subst(fun.returnType.getOrElse(Any))
                case meth: PsiMethod => s.subst(ScType.create(meth.getReturnType, getProject))
              }
            }
            case _ => Nothing
          }
        }
        //todo: add implicit types check
      }
      val res = getInvokedExpr.getType(TypingContext.empty) match {
        case Success(ScFunctionType(retType: ScType, params: Seq[ScType]), _) => {
          retType
        }
        case Success(tp: ScType, _) => {
          ScType.extractClassType(tp) match {
            case Some((clazz: PsiClass, subst: ScSubstitutor)) => {
              clazz match {
                case clazz: ScClass if clazz.isCase => tp //todo: this is wrong if reference isn't class name
                case _ => processType(tp)
              }
            }
            case _ => tp
          }
        }
        case x => x.getOrElse(return x)
      }
      def isOneMoreCall(elem: PsiElement): Boolean = {
        elem.getParent match {
          case _: ScMethodCall => true
          case _: ScUnderscoreSection => true
          case _: ScParenthesisedExpr => isOneMoreCall(elem.getParent)
          case _ => false
        }
      }
      //conversion for implicit clause
      res match {
        case tp: ScFunctionType if tp.isImplicit && !isOneMoreCall(this) => tp.returnType
        case tp => tp
      }
    }

    Success(inner, Some(this))
  }
}