package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import api.toplevel.ScTypedDefinition
import api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import toplevel.synthetic.ScSyntheticFunction
import types._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import result.{Failure, Success, TypeResult, TypingContext}
import api.statements.{ScFun, ScFunction}
import api.base.types.ScTypeElement
import lang.resolve.{ResolveUtils, MethodResolveProcessor, ScalaResolveResult}

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScGenericCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScGenericCall {
  override def toString: String = "GenericCall"


  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val typeResult = referencedExpr.getType(ctx)
    val refType = typeResult.getOrElse(return typeResult)

    /**
     * Utility method to get generics for apply methods of concrecte class.
     */
    def processType(tp: ScType): ScType = {
      val curr = getParent match {case call: ScMethodCall => call case _ => this}
      val isUpdate = curr.getContext.isInstanceOf[ScAssignStmt] &&
              curr.getContext.asInstanceOf[ScAssignStmt].getLExpression == curr
      val methodName = if (isUpdate) "update" else "apply"
      val args: Seq[ScExpression] = (curr match {case call: ScMethodCall => call.args.exprs
        case _ => Seq.empty[ScExpression]}) ++ (
              if (isUpdate) getContext.asInstanceOf[ScAssignStmt].getRExpression match {
                case Some(x) => Seq[ScExpression](x)
                case None =>
                  Seq[ScExpression](ScalaPsiElementFactory.createExpressionFromText("{val x: Nothing = null; x}",
                    getManager)) //we can't to not add something => add Nothing expression
              }
              else Seq.empty)
      val typeArgs: Seq[ScTypeElement] = this.arguments
      import Compatibility.Expression._
      val processor = new MethodResolveProcessor(referencedExpr, methodName, args :: Nil,
        typeArgs, curr.expectedType)
      processor.processType(tp, referencedExpr, ResolveState.initial)
      val candidates = processor.candidates
      if (candidates.length != 1) Nothing
      else {
        candidates(0) match {
          case ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor) => {
            fun match {
              case fun: ScFun => new ScFunctionType(s.subst(fun.retType),
                collection.immutable.Seq(fun.paramTypes.map({
                  s.subst _
                }).toSeq: _*), fun.getProject)
              case fun: ScFunction => s.subst(fun.getType(TypingContext.empty).getOrElse(Nothing))
              case meth: PsiMethod => ResolveUtils.methodType(meth, s)
            }
          }
          case _ => Nothing
        }
      }
      //todo: add implicit types check
    }

    // here we get generic names to replace with appropriate substitutor to appropriate types
    val tp: Seq[String] = referencedExpr match {
      case expr: ScReferenceExpression => expr.bind match {
        case Some(ScalaResolveResult(fun: ScFunction, _)) => fun.typeParameters.map(_.name)
        case Some(ScalaResolveResult(meth: PsiMethod, _)) => meth.getTypeParameters.map(_.getName)
        case Some(ScalaResolveResult(synth: ScSyntheticFunction, _)) => synth.typeParams.map(_.name)
        case Some(ScalaResolveResult(clazz: ScObject, subst)) => {
          return Success(processType(ScDesignatorType(clazz)), None)
        }
        case Some(ScalaResolveResult(clazz: ScClass, _)) if clazz.hasModifierProperty("case") => {
          clazz.typeParameters.map(_.name)
        }
        case Some(ScalaResolveResult(typed: ScTypedDefinition, subst)) => { //here we must investigate method apply (not update, because can't be generic)
          val scType = subst.subst(typed.getType(TypingContext.empty).getOrElse(Any))
          return Success(processType(scType), Some(this))
        }
        case _ => return Failure("Cannot infer the type", Some(this)) //todo: check Java cases (PsiField for example)
      }
      case _ => { //here we must investigate method apply (not update, because can't be generic)
        return Success(processType(refType), Some(this))
      }
    }
    val substitutor = ScalaPsiUtil.genericCallSubstitutor(tp, this)
    Success(substitutor.subst(refType), Some(this))
  }
}