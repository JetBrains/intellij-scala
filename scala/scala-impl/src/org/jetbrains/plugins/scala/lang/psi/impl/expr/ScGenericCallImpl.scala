package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor._


/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScGenericCallImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScGenericCall {

  /**
    * Utility method to get generics for apply methods of concrecte class.
    */
  private def processType(tp: ScType, isShape: Boolean): ScType = {
    val curr = getContext match {
      case call: ScMethodCall => call
      case _ => this
    }
    val isUpdate = curr.getContext.isInstanceOf[ScAssignStmt] &&
      curr.getContext.asInstanceOf[ScAssignStmt].getLExpression == curr
    val methodName = if (isUpdate) "update" else "apply"
    val args: List[Seq[ScExpression]] =
      if (curr == this && !isUpdate) List.empty
      else {
        (curr match {
          case call: ScMethodCall => call.args.exprs
          case _ => Seq.empty[ScExpression]
        }) ++ (
          if (isUpdate) curr.getContext.asInstanceOf[ScAssignStmt].getRExpression match {
            case Some(x) => Seq[ScExpression](x)
            case None =>
              Seq[ScExpression](createExpressionFromText("{val x: Nothing = null; x}"))
            //we can't to not add something => add Nothing expression
          }
          else Seq.empty) :: Nil
      }
    val typeArgs: Seq[ScTypeElement] = this.arguments
    import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression._
    val processor = new MethodResolveProcessor(referencedExpr, methodName, args, typeArgs,
      Seq.empty /* todo: ? */ , isShapeResolve = isShape, enableTupling = true)
    processor.processType(tp, referencedExpr, ResolveState.initial)
    processor.candidates match {
      case Array(ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
        fun.polymorphicType(s)
      case _ => Nothing
    }
  }


  private def convertReferencedType(typeResult: TypeResult): TypeResult = {
    var refType = typeResult.getOrElse(return typeResult)
    if (!refType.isInstanceOf[ScTypePolymorphicType]) refType = processType(refType, isShape = false)
    refType match {
      case ScTypePolymorphicType(int, tps) =>
        val subst = ScalaPsiUtil.genericCallSubstitutor(tps.map(_.nameAndId), this)
        Right(subst.subst(int))
      case _ => Right(refType)
    }
  }

  private def shapeType(typeResult: TypeResult): TypeResult = {
    var refType = typeResult.getOrElse(return typeResult)
    if (!refType.isInstanceOf[ScTypePolymorphicType]) refType = processType(refType, isShape = true)
    refType match {
      case ScTypePolymorphicType(int, tps) =>
        val subst = ScalaPsiUtil.genericCallSubstitutor(tps.map(_.nameAndId), this)
        Right(subst.subst(int))
      case _ => Right(refType)
    }
  }


  protected override def innerType: TypeResult = {
    val typeResult = referencedExpr.getNonValueType()
    convertReferencedType(typeResult)
  }

  def shapeType: TypeResult = {
    val typeResult: TypeResult = referencedExpr match {
      case ref: ScReferenceExpression => ref.shapeType
      case expr => expr.getNonValueType()
    }
    shapeType(typeResult)
  }

  def shapeMultiType: Array[TypeResult] = {
    val typeResult: Array[TypeResult] = referencedExpr match {
      case ref: ScReferenceExpression => ref.shapeMultiType
      case expr => Array(expr.getNonValueType())
    }
    typeResult.map(shapeType(_))
  }

  override def shapeMultiResolve: Option[Array[ResolveResult]] = {
    referencedExpr match {
      case ref: ScReferenceExpression => Some(ref.shapeResolve)
      case _ => None
    }
  }

  def multiType: Array[TypeResult] = {
    val typeResult: Array[TypeResult] = referencedExpr match {
      case ref: ScReferenceExpression => ref.multiType
      case expr => Array(expr.getNonValueType())
    }
    typeResult.map(convertReferencedType)
  }

  override def multiResolve: Option[Array[ResolveResult]] = {
    referencedExpr match {
      case ref: ScReferenceExpression => Some(ref.multiResolve(false))
      case _ => None
    }
  }

  override def toString: String = "GenericCall"
}
