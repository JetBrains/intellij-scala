package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import types._
import com.intellij.psi._
import nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import api.statements.{ScFun, ScFunction}
import api.base.types.ScTypeElement
import lang.resolve.{ResolveUtils, ScalaResolveResult}
import lang.resolve.processor._
import result._
import api.ScalaElementVisitor

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScGenericCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScGenericCall {
  override def toString: String = "GenericCall"



  /**
   * Utility method to get generics for apply methods of concrecte class.
   */
  private def processType(tp: ScType, isShape: Boolean): ScType = {
    val curr = getContext match {case call: ScMethodCall => call case _ => this}
    val isUpdate = curr.getContext.isInstanceOf[ScAssignStmt] &&
            curr.getContext.asInstanceOf[ScAssignStmt].getLExpression == curr
    val methodName = if (isUpdate) "update" else "apply"
    val args: List[Seq[ScExpression]] =
      if (curr == this && !isUpdate) List.empty
      else {
        (curr match {case call: ScMethodCall => call.args.exprs
        case _ => Seq.empty[ScExpression]}) ++ (
                if (isUpdate) curr.getContext.asInstanceOf[ScAssignStmt].getRExpression match {
                  case Some(x) => Seq[ScExpression](x)
                  case None =>
                    Seq[ScExpression](ScalaPsiElementFactory.createExpressionFromText("{val x: Nothing = null; x}",
                      getManager)) //we can't to not add something => add Nothing expression
                }
                else Seq.empty) :: Nil
      }
    val typeArgs: Seq[ScTypeElement] = this.arguments
    import Compatibility.Expression._
    val processor = new MethodResolveProcessor(referencedExpr, methodName, args, typeArgs,
      Seq.empty /* todo: ? */, isShapeResolve = isShape, enableTupling = true)
    processor.processType(tp, referencedExpr, ResolveState.initial)
    val candidates = processor.candidates
    if (candidates.length != 1) types.Nothing
    else {
      candidates(0) match {
        case ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor) => {
          fun match {
            case fun: ScFun => s.subst(fun.polymorphicType)
            case fun: ScFunction => s.subst(fun.polymorphicType())
            case meth: PsiMethod => ResolveUtils.javaPolymorphicType(meth, s, getResolveScope)
          }
        }
        case _ => types.Nothing
      }
    }
  }

  private def convertReferencedType(typeResult: TypeResult[ScType]): TypeResult[ScType] = {
    var refType = typeResult.getOrElse(return typeResult)
    if (!refType.isInstanceOf[ScTypePolymorphicType]) refType = processType(refType, isShape = false)
    refType match {
      case ScTypePolymorphicType(int, tps) =>
        val subst = ScalaPsiUtil.genericCallSubstitutor(tps.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p.ptp))), this)
        Success(subst.subst(int), Some(this))
      case _ => Success(refType, Some(this))
    }
  }


  private def shapeType(typeResult: TypeResult[ScType]): TypeResult[ScType] = {
    var refType = typeResult.getOrElse(return typeResult)
    if (!refType.isInstanceOf[ScTypePolymorphicType]) refType = processType(refType, isShape = true)
    refType match {
      case ScTypePolymorphicType(int, tps) => {
        val subst = ScalaPsiUtil.genericCallSubstitutor(tps.map(p => (p.name, ScalaPsiUtil.getPsiElementId(p.ptp))), this)
        Success(subst.subst(int), Some(this))
      }
      case _ => Success(refType, Some(this))
    }
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val typeResult = referencedExpr.getNonValueType(ctx)
    convertReferencedType(typeResult)
  }

  def shapeType: TypeResult[ScType] = {
    val typeResult: TypeResult[ScType] = referencedExpr match {
      case ref: ScReferenceExpression => ref.shapeType
      case expr => expr.getNonValueType(TypingContext.empty)
    }
    shapeType(typeResult)
  }

  def shapeMultiType: Array[TypeResult[ScType]] = {
    val typeResult: Array[TypeResult[ScType]] = referencedExpr match {
      case ref: ScReferenceExpression => ref.shapeMultiType
      case expr => Array(expr.getNonValueType(TypingContext.empty))
    }
    typeResult.map(shapeType(_))
  }

  def multiType: Array[TypeResult[ScType]] = {
    val typeResult: Array[TypeResult[ScType]] = referencedExpr match {
      case ref: ScReferenceExpression => ref.multiType
      case expr => Array(expr.getNonValueType(TypingContext.empty))
    }
    typeResult.map(convertReferencedType)
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitGenericCallExpression(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitGenericCallExpression(this)
      case _ => super.accept(visitor)
    }
  }
}