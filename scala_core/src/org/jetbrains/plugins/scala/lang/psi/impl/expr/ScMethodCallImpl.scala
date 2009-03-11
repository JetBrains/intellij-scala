package org.jetbrains.plugins.scala.lang.psi.impl.expr

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import api.statements.{ScFunction, ScFun}
import api.toplevel.ScTyped
import api.toplevel.typedef.{ScClass, ScObject}
import com.intellij.psi.{PsiMethod, PsiElement, PsiClass}
import types.{ScType, PhysicalSignature, ScFunctionType, ScSubstitutor}
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.expr._
import toplevel.synthetic.ScSyntheticFunction
import types.Nothing

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
      def createSubst(method: PsiMethod): ScSubstitutor = {
        //here we don't care for generics because this case was filtered
        //todo: type erasure
        subst
      }
      val params: Seq[ScExpression] = this.args.exprs  //todo: add aditional argument in case for update methods
      val applyMethods = ScalaPsiUtil.getApplyMethods(clazz) //todo: add update methods
      val methods = ScalaPsiUtil.getMethodsConforsToMethodCall(applyMethods, params, createSubst(_))
      if (methods.length == 1) {
        val method = methods(0).method
        val typez = method match {
          case fun: ScFunction => fun.returnType
          case meth: PsiMethod => ScType.create(meth.getReturnType, meth.getProject)
        }
        return createSubst(method).subst(typez)
      } else {
        return Nothing
        //todo: according to expected type choose appropriate method if it's possible, else => Nothing
      }
    }

    //method used to convert expression to return type
    def tail: ScType = {
      getInvokedExpr.getType match {
        case ScFunctionType(r, _) => r
        case t => ScType.extractClassType(t) match {
          case Some((clazz: PsiClass, subst: ScSubstitutor)) => return processClass(clazz, subst)
          case _ => return Nothing
        }
      }
    }

    getInvokedExpr match {
      //if it's generic call, so we know that type will be substituted right and to not double functionality
      case call: ScGenericCall => return tail
      //If we have reference we must to check type erasure
      case ref: ScReferenceExpression => {
        val bind = ref.bind
        bind match {
          //three methods cases
          case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.typeParameters.length == 0 => return tail
          case Some(ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor)) => {
            //todo: type erasure to get type params implicitly
            return tail
          }
          case Some(ScalaResolveResult(fun: ScFun, _)) if fun.typeParameters.length == 0 => return tail
          case Some(ScalaResolveResult(fun: ScFun, subst: ScSubstitutor)) => {
            //todo: type erasure to get type params implicitly
            return tail
          }
          case Some(ScalaResolveResult(meth: PsiMethod, _)) if meth.getTypeParameters.length == 0 => return tail
          case Some(ScalaResolveResult(meth: PsiMethod, subst: ScSubstitutor)) => {
            //todo: type erasure to get type params implicitly
            return tail
          }
          //if we resolve to object, so should try to check apply and update methods
          case Some(ScalaResolveResult(obj: ScObject, subst: ScSubstitutor)) => {
            return processClass(obj, subst)
          }
          case Some(ScalaResolveResult(typed: ScTyped, _)) => return tail
          case _ => return Nothing //todo: case classes, java cases (PsiField for example)
        }
      }
      case _ => {
        return tail
      }
    }
  }
}