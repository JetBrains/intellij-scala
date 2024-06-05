package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil.kindProjectorPolymorphicLambdaType
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.PolymorphicLambda
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider.PsiMethodTypeProviderExt
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.ScExpressionForExpectedTypesEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.ScTypeForDynamicProcessorEx

class ScGenericCallImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScGenericCall {
  private def processApplyOrUpdateMethod(tp: ScType, shapesOnly: Boolean): ScType = {
    val applyCandidates = this.resolveApplyMethod(
      this,
      tp,
      shapesOnly    = shapesOnly,
      stripTypeArgs = false,
      withImplicits = true
    )

    applyCandidates match {
      case Array(srr @ ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
        fun
          .methodTypeProvider(elementScope)
          .polymorphicType(s)
          .updateTypeOfDynamicCall(srr.isDynamic)
      case _ =>
        Nothing
    }
  }

  private def substPolymorphicType: ScType => ScType = {
    case ScTypePolymorphicType(internal, tps) =>
      //type parameters of a method are appended to the right of ScTypePolymorphicType parameters
      val subst = ScSubstitutor.bind(tps.takeRight(arguments.length), arguments)(_.calcType)
      val substedInternal = subst(internal)

      if (arguments.length < tps.length) ScTypePolymorphicType(subst(internal), tps.dropRight(arguments.length))
      else                               substedInternal
    case t => t
  }

  private def processNonPolymorphic(shapesOnly: Boolean): ScType => ScType = {
    case p: ScTypePolymorphicType => p
    case t                        => processApplyOrUpdateMethod(t, shapesOnly)
  }

  private def convertReferencedType(typeResult: TypeResult, shapesOnly: Boolean): TypeResult = {
    typeResult
      .map(processNonPolymorphic(shapesOnly))
      .map(substPolymorphicType)
  }

  private val polymorphicLambdaType = cached("polymorphicLambdaType", ModTracker.physicalPsiChange(getProject), () => {
    this match {
      case PolymorphicLambda(des, lhs, rhs) => kindProjectorPolymorphicLambdaType(des, lhs, rhs).asTypeResult
      case _                                => Failure(ScalaBundle.message("not.a.polymorphic.lambda"))
    }
  })

  protected override def innerType: TypeResult =
    polymorphicLambdaType().left.flatMap { _ =>
      val typeResult = referencedExpr.getNonValueType()
      convertReferencedType(typeResult, shapesOnly = false)
    }

  override def shapeType: TypeResult =
    polymorphicLambdaType().left.flatMap { _ =>
      val typeResult: TypeResult = referencedExpr match {
        case ref: ScReferenceExpression => ref.shapeType
        case expr => expr.getNonValueType()
      }
      convertReferencedType(typeResult, shapesOnly = true)
    }

  override def shapeMultiType: Array[TypeResult] = {
    val polyLambdaType = polymorphicLambdaType()
    if (polyLambdaType.isLeft) {
      val typeResult: Array[TypeResult] = referencedExpr match {
        case ref: ScReferenceExpression => ref.shapeMultiType
        case expr                       => Array(expr.getNonValueType())
      }
      typeResult.map(convertReferencedType(_, shapesOnly = true))
    } else Array(polyLambdaType)
  }

  override def shapeMultiResolve: Option[Array[ScalaResolveResult]] = {
    referencedExpr match {
      case ref: ScReferenceExpression => Some(ref.shapeResolve)
      case _                          => None
    }
  }

  override def multiType: Array[TypeResult] = {
    val polyLambdaType = polymorphicLambdaType()
    if (polyLambdaType.isLeft) {
      val typeResult: Array[TypeResult] = referencedExpr match {
        case ref: ScReferenceExpression => ref.multiType
        case expr => Array(expr.getNonValueType())
      }
      typeResult.map(convertReferencedType(_, shapesOnly = false))
    } else Array(polyLambdaType)
  }

  override def multiResolve: Option[Array[ScalaResolveResult]] = {
    referencedExpr match {
      case ref: ScReferenceExpression => Some(ref.multiResolveScala(false))
      case _                          => None
    }
  }

  override def bindInvokedExpr: Option[ScalaResolveResult] = referencedExpr match {
    case ref: ScReferenceExpression => ref.bind()
    case _                          => None
  }

  override def toString: String = "GenericCall"
}
