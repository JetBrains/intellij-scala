package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil.kindProjectorPolymorphicLambdaType
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.PolymorphicLambda
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.Ext
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.Nothing
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider.PsiMethodTypeProviderExt
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.PsiElementForExpectedTypesEx
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.ScTypeForDynamicProcessorEx

class ScGenericCallImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScGenericCall {
  private def substPolymorphicType: ScType => ScType = {
    case ScTypePolymorphicType(internal, tps) =>
      val targs            = typeArguments
      val targNames        = targs.flatMap(_.name)
      val hasNamedTypeArgs = targNames.nonEmpty

      val subst =
        if (hasNamedTypeArgs) ScSubstitutor.bind(tps, targs)
        else                  ScSubstitutor.bind(tps.take(targs.length), targs)

      val substedInternal = subst(internal)

      val trimmedTypeParams =
        if (hasNamedTypeArgs) tps.filterNot(tp => targNames.contains(tp.name))
        else                  tps.drop(targs.length)

      if (targs.length < tps.length) ScTypePolymorphicType(subst(internal), trimmedTypeParams)
      else                           substedInternal
    case t => t
  }

  /**
   * Normally when we have `foo[Int, String]` the type can be completely inferred from the provided
   * args and is not dependent on the expected type. However, with the addition of named type
   * arguments, we can now have cases like `foo[A = Int]` (where we have to infer the remaining arguments).
   */
  private def updateWithExpected(tpe: ScType): ScType = tpe match {
    case tpt: ScTypePolymorphicType =>
      InferUtil.updateAccordingToExpectedType(
        tpt,
        filterTypeParams = true,
        this.expectedType(),
        this,
        canThrowSCE = false
      )
    case _ => tpe
  }

  private def processNonPolymorphic(isShape: Boolean): ScType => ScType = {
    case p: ScTypePolymorphicType => p
    case t                        => ScGenericCallImpl.processApplyOrUpdateMethod(this, t, isShape)
  }

  private def convertReferencedType(typeResult: TypeResult, isShape: Boolean): TypeResult = {
    typeResult
      .map {
        //e.g. def foo(using A)[B]: Int = 1; foo[Int]
        case mt: ScMethodType if mt.isImplicit && !isShape =>
          val (updated, implicits) =
            this.updatedWithImplicitArguments(
              mt,
              checkExpectedType = false,
              updateDeep = false,
              isLeadingClause = true
            )

          setImplicitArguments(implicits)
          updated
        case tpe => tpe
      }
      .map(processNonPolymorphic(isShape))
      .map(substPolymorphicType)
      .map(tpe =>
        if (isShape) tpe
        else         updateWithExpected(tpe)
      )
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
        case expr                       => expr.getNonValueType()
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

object ScGenericCallImpl {
  def processApplyOrUpdateMethod(
    gen:        ScGenericCall,
    tp:         ScType,
    shapesOnly: Boolean
  ): ScType = {
    def workWithApplyCandidates(candidates: Array[ScalaResolveResult]): Option[ScType] = candidates match {
      case Array(srr @ ScalaResolveResult(fun: PsiMethod, s: ScSubstitutor)) =>
        fun
          .methodTypeProvider(gen.elementScope)
          .polymorphicType(s)
          .updateTypeOfDynamicCall(srr.isDynamic)
          .toOption
      case _ => None
    }

    val applyCandidates = gen.resolveApplyOrUpdateMethod(
      gen.referencedExpr,
      tp,
      shapesOnly    = shapesOnly,
      withImplicits = true
    )

    workWithApplyCandidates(applyCandidates) match {
      case Some(tp) => tp
      case None     => Nothing(gen.projectContext)
    }
  }
}
