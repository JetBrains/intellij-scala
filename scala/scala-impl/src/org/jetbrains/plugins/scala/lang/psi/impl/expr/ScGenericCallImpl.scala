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
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import org.jetbrains.plugins.scala.util.KindProjectorUtil.PolymorphicLambda

import scala.collection.Seq


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
    val isUpdate = curr.getContext.isInstanceOf[ScAssignment] &&
      curr.getContext.asInstanceOf[ScAssignment].getLExpression == curr
    val methodName = if (isUpdate) "update" else "apply"
    val args: List[Seq[ScExpression]] =
      if (curr == this && !isUpdate) List.empty
      else {
        (curr match {
          case call: ScMethodCall => call.args.exprs
          case _ => Seq.empty[ScExpression]
        }) ++ (
          if (isUpdate) curr.getContext.asInstanceOf[ScAssignment].getRExpression match {
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
        fun.methodTypeProvider(elementScope).polymorphicType(s)
      case _ => Nothing
    }
  }

  private def substPolymorphicType: ScType => ScType = {
    case ScTypePolymorphicType(internal, tps) =>
      //type parameters of a method are appended to the right of ScTypePolymorphicType parameters
      ScSubstitutor.bind(tps.takeRight(arguments.length), arguments)(_.calcType)
        .apply(internal)
    case t => t
  }

  private def processNonPolymorphic(isShape: Boolean): ScType => ScType = {
    case p: ScTypePolymorphicType => p
    case t => processType(t, isShape)
  }

  private def convertReferencedType(typeResult: TypeResult, isShape: Boolean): TypeResult = {
    typeResult
      .map(processNonPolymorphic(isShape))
      .map(substPolymorphicType)
  }

  @Cached(ModCount.getModificationCount, this)
  private def polymorphicLambdaType: TypeResult = this match {
    case PolymorphicLambda(resTpe) => Right(resTpe)
    case _                         => Failure("Not a polymorphic lambda.")
  }

  protected override def innerType: TypeResult =
    polymorphicLambdaType.left.flatMap { _ =>
      val typeResult = referencedExpr.getNonValueType()
      convertReferencedType(typeResult, isShape = false)
    }

  def shapeType: TypeResult =
    polymorphicLambdaType.left.flatMap { _ =>
      val typeResult: TypeResult = referencedExpr match {
        case ref: ScReferenceExpression => ref.shapeType
        case expr => expr.getNonValueType()
      }
      convertReferencedType(typeResult, isShape = true)
    }

  def shapeMultiType: Array[TypeResult] = {
    val polyLambdaType = polymorphicLambdaType
    if (polyLambdaType.isLeft) {
      val typeResult: Array[TypeResult] = referencedExpr match {
        case ref: ScReferenceExpression => ref.shapeMultiType
        case expr                       => Array(expr.getNonValueType())
      }
      typeResult.map(convertReferencedType(_, isShape = true))
    } else Array(polyLambdaType)
  }

  override def shapeMultiResolve: Option[Array[ScalaResolveResult]] = {
    referencedExpr match {
      case ref: ScReferenceExpression => Some(ref.shapeResolve)
      case _ => None
    }
  }

  def multiType: Array[TypeResult] = {
    val polyLambdaType = polymorphicLambdaType
    if (polyLambdaType.isLeft) {
      val typeResult: Array[TypeResult] = referencedExpr match {
        case ref: ScReferenceExpression => ref.multiType
        case expr => Array(expr.getNonValueType())
      }
      typeResult.map(convertReferencedType(_, isShape = false))
    } else Array(polyLambdaType)
  }

  override def multiResolve: Option[Array[ScalaResolveResult]] = {
    referencedExpr match {
      case ref: ScReferenceExpression => Some(ref.multiResolveScala(false))
      case _ => None
    }
  }

  override def toString: String = "GenericCall"
}
