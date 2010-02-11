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
import nonvalue.ScTypePolymorphicType
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



  /**
   * Utility method to get generics for apply methods of concrecte class.
   */
  private def processType(tp: ScType): ScType = {
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
      typeArgs, None)
    processor.processType(tp, referencedExpr, ResolveState.initial)
    val candidates = processor.candidates
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


  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val typeResult = referencedExpr.getNonValueType(ctx)
    var refType = typeResult.getOrElse(return typeResult)
    if (!refType.isInstanceOf[ScTypePolymorphicType]) refType = processType(refType)
    refType match {
      case ScTypePolymorphicType(int, tps) => {
        val subst = ScalaPsiUtil.genericCallSubstitutor(tps.map(_.name), this)
        Success(subst.subst(int), Some(this))
      }
      case _ => Success(refType, Some(this))
    }
  }
}