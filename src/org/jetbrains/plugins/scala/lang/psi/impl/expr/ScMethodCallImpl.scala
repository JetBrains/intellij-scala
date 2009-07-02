package org.jetbrains.plugins.scala.lang.psi.impl.expr

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
    //todo: add logic to get type for methods with implicit parameter clause
    /**
     * Utility method to get type for apply (and update) methods of concrecte class.
     */
    def processClass(clazz: PsiClass, subst: ScSubstitutor): ScType = {
      //ugly method for appling it to methods chooser (to substitute types for every method)
      def createSubst(method: PhysicalSignature): ScSubstitutor = {
        //here we don't care for generics because this case was filtered
        //todo: type erasure
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

    //method used to convert expression to return type
    def tail(convertType: Boolean): ScType = {
      getInvokedExpr.cachedType match {
        case ScFunctionType(r, _) => r        
        case pt: ScProjectionType if convertType => pt.element match {
          //todo Should this match be more general? For now, it is just enough to pass the test case:
          //     ImplicitCallScl1024.scala
          case Some(synth: ScSyntheticClass) => synth.t
          case _ => return Nothing            
        }
        case t: StdType => t //do not convert std type
        case t: ScSingletonType if convertType => {
          t.pathType match {
            case ScSingletonType(path: ScStableCodeReferenceElement) => {
              /*todo: it may be useful to delete this case, and
                todo: remove Singleton type return from t.pathType*/
              path.bind match {
                case Some(ScalaResolveResult(clazz: PsiClass, subst: ScSubstitutor)) => return processClass(clazz, subst)
                case _ => return Nothing
              }
            }
            case t => {
              ScType.extractDesignated(t) match {
                case Some((clazz: PsiClass, subst: ScSubstitutor)) => return processClass(clazz, subst)
                case _ => return Nothing
              }
            }
          }
        }
        case t if convertType => ScType.extractDesignated(t) match {
          case Some((clazz: PsiClass, subst: ScSubstitutor)) => return processClass(clazz, subst)
          case _ => return Nothing
        }
        case t => t
      }
    }

    getInvokedExpr match {
      //if it's generic call, so we know that type will be substituted right and to not double functionality
      case call: ScGenericCall => return tail(false)
      //If we have reference we must to check type erasure
      case ref: ScReferenceExpression => {
        val bind = ref.bind
        bind match {
          //three methods cases
          case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.typeParameters.length == 0 => return tail(true)
          case Some(ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor)) => {
//            val signature = new PhysicalSignature(fun, subst)
//            val types = signature.types
//            val argList = args
            //todo: get type params implicitly
            return tail(true)
          }
          case Some(ScalaResolveResult(fun: ScFun, _)) if fun.typeParameters.length == 0 => return tail(true)
          case Some(ScalaResolveResult(fun: ScFun, subst: ScSubstitutor)) => {
            //todo: get type params implicitly
            return tail(true)
          }
          case Some(ScalaResolveResult(meth: PsiMethod, _)) if meth.getTypeParameters.length == 0 => return tail(true)
          case Some(ScalaResolveResult(meth: PsiMethod, subst: ScSubstitutor)) => {
            //todo: get type params implicitly
            return tail(true)
          }
          //if we resolve to object, so should try to check apply and update methods
          case Some(ScalaResolveResult(obj: ScObject, subst: ScSubstitutor)) => {
            return processClass(obj, subst)
          }
          //case classes
          case Some(ScalaResolveResult(clazz: ScClass, subst: ScSubstitutor)) if clazz.isCase && clazz.typeParameters.length == 0 => {
            return getInvokedExpr.cachedType
          }
          case Some(ScalaResolveResult(clazz: ScClass, subst: ScSubstitutor)) if clazz.isCase => {
            //todo: get type params implicitly
            return getInvokedExpr.cachedType
          }
          case Some(ScalaResolveResult(typed: ScTyped, _)) => return tail(true)
          case Some(ScalaResolveResult(field: PsiField, _)) => return tail(true)
          case _ => return Nothing
        }
      }
      case _ => {
        return tail(true)
      }
    }
  }
}