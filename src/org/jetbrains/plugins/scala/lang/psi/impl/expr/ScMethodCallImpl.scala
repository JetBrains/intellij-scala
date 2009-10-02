package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import api.base.ScStableCodeReferenceElement
import api.statements.{ScFunction, ScFun}
import api.toplevel.ScTyped
import api.toplevel.typedef.{ScClass, ScObject}
import com.intellij.psi.{PsiMethod, PsiField, PsiElement, PsiClass}
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import toplevel.synthetic.{ScSyntheticClass, ScSyntheticFunction}
import types._
/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMethodCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMethodCall {
  override def toString: String = "MethodCall"

  override def getType: ScType = {
    /**
     * Utility method to get type for apply (and update) methods of concrecte class.
     */
    def processClass(clazz: PsiClass, subst: ScSubstitutor): ScType = {
      //ugly method for appling it to methods chooser (to substitute types for every method)
      def createSubst(method: PhysicalSignature): ScSubstitutor = {
        //here we don't care for generics because this case was filtered
        method.substitutor.followed(subst)
      }
      val isUpdate = getContext.isInstanceOf[ScAssignStmt] && getContext.asInstanceOf[ScAssignStmt].getLExpression == this
      val args: Seq[ScExpression] = this.args.exprs ++ (
              if (isUpdate) getContext.asInstanceOf[ScAssignStmt].getRExpression match {
                case Some(x) => Seq[ScExpression](x)
                case None =>
                  Seq[ScExpression](ScalaPsiElementFactory.createExpressionFromText("{val x: Nothing = null; x}",
                    clazz.getManager)) //we can't to not add something => add Nothing expression
              }
              else Seq.empty)
      val applyMethods = if (!isUpdate) ScalaPsiUtil.getApplyMethods(clazz) else ScalaPsiUtil.getUpdateMethods(clazz)
      val methods = ScalaPsiUtil.getMethodsConformingToMethodCall(applyMethods, args, createSubst(_))
      if (methods.length == 1) {
        val method = methods(0).method
        val typez = method match {
          case fun: ScFunction => fun.returnType  
          case meth: PsiMethod => ScType.create(meth.getReturnType, meth.getProject)
        }
        return createSubst(methods(0)).subst(typez)
      } else {
        return Nothing
        //todo: according to expected type choose appropriate method if it's possible, else => Nothing
      }
    }
    val invokedType = getInvokedExpr.getType
    if (invokedType == types.Nothing) return Nothing
    invokedType match {
      case ScFunctionType(retType: ScType, params: Seq[ScType]) => {
        retType
      }
      case tp: ScType => {
        ScType.extractClassType(tp) match {
          case Some((clazz: PsiClass, subst: ScSubstitutor)) => {
            clazz match {
              case clazz: ScClass if clazz.isCase => tp //todo: infer implicit generic type
              case _ => processClass(clazz, subst)
            }
          }
          case _ => Nothing
        }
      }
    }
  }
}