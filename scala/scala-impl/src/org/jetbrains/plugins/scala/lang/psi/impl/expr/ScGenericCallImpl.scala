package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions.ObjectExt
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

  //This is a little bit tricky, since `foo[A](b)` can be either
  //1. `foo.apply[A](b)` or 2. `foo.apply[A].apply(b)` and to resolve apply method correctly,
  //we must provide resolve processor with a correct set of args.
  //Since 1. is probably the more common scenario, let's first try to resolve with args,
  //and if it fails try 2.
  private def processApplyOrUpdateMethod(tp: ScType, shapesOnly: Boolean): ScType = {
    def workWithApplyCandidates(candidates: Array[ScalaResolveResult]): Option[ScType] = candidates match {
      case Array(srr @ ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
        fun
          .methodTypeProvider(elementScope)
          .polymorphicType(s)
          .updateTypeOfDynamicCall(srr.isDynamic)
          .toOption
      case _ => None
  }

    val applyResolveContext = getContext match {
      case inv: MethodInvocation if inv.getInvokedExpr == this => inv
      case _                                                   => this
    }

    val applyCandidates = this.resolveApplyOrUpdateMethod(
      applyResolveContext,
      tp,
      shapesOnly    = shapesOnly,
      stripTypeArgs = false,
      withImplicits = true
    )

    workWithApplyCandidates(applyCandidates) match {
      case Some(tp) => tp
      case None =>
        if (applyResolveContext.is[MethodInvocation]) {
          val applyCandidatesWithoutArgs =
            this.resolveApplyOrUpdateMethod(
              this,
              tp,
              shapesOnly    = shapesOnly,
              stripTypeArgs = false,
              withImplicits = true
            )

          workWithApplyCandidates(applyCandidatesWithoutArgs).getOrElse(Nothing)
        } else Nothing
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

  private def processNonPolymorphic(isShape: Boolean): ScType => ScType = {
    case p: ScTypePolymorphicType => p
    case t                        => processApplyOrUpdateMethod(t, isShape)
  }

  private def convertReferencedType(typeResult: TypeResult, isShape: Boolean): TypeResult = {
    typeResult
      .map(processNonPolymorphic(isShape))
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
      convertReferencedType(typeResult, isShape = false)
    }

  override def shapeType: TypeResult =
    polymorphicLambdaType().left.flatMap { _ =>
      val typeResult: TypeResult = referencedExpr match {
        case ref: ScReferenceExpression => ref.shapeType
        case expr => expr.getNonValueType()
      }
      convertReferencedType(typeResult, isShape = true)
    }

  override def shapeMultiType: Array[TypeResult] = {
    val polyLambdaType = polymorphicLambdaType()
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
      typeResult.map(convertReferencedType(_, isShape = false))
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
