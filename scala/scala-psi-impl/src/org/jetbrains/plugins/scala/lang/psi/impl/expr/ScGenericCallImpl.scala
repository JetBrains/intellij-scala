package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil.kindProjectorPolymorphicLambdaType
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.PolymorphicLambda
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiBundle
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
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.ScTypeForDynamicProcessorEx
import org.jetbrains.plugins.scala.lang.resolve.{ResolveMode, ScalaResolveResult}

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
  private def updateWithExpected(tpe: ScType, expectedType: Option[ScType]): ScType = tpe match {
    case tpt: ScTypePolymorphicType =>
      InferUtil.updateAccordingToExpectedType(
        tpt,
        filterTypeParams = true,
        expectedType,
        this,
        canThrowSCE = false
      )
    case _ => tpe
  }

  private def processNonPolymorphic(mode: ResolveMode): ScType => ScType = {
    case p: ScTypePolymorphicType => p
    case t                        => ScGenericCallImpl.processApplyOrUpdateMethod(this, t, mode)
  }

  /**
   * @param mode         the mode the referenced expression was resolved in. Leading implicit clauses are only applied,
   *                     and the expected type is only taken into account, with [[ResolveMode.Full]]
   * @param expectedType the expected type of this generic call, only used with [[ResolveMode.Full]]
   */
  private def convertReferencedType(typeResult: TypeResult, mode: ResolveMode, expectedType: => Option[ScType] = None): TypeResult = {
    val isShape = mode != ResolveMode.Full

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

          setImplicitArguments(implicits, expectedType)
          updated
        case tpe => tpe
      }
      .map(processNonPolymorphic(mode))
      .map(substPolymorphicType)
      .map(tpe =>
        if (isShape) tpe
        else         updateWithExpected(tpe, expectedType)
      )
  }

  private val polymorphicLambdaType = cached("polymorphicLambdaType", ModTracker.physicalPsiChange(getProject), () => {
    this match {
      case PolymorphicLambda(des, lhs, rhs) => kindProjectorPolymorphicLambdaType(des, lhs, rhs).asTypeResult
      case _                                => Failure(ScalaPsiBundle.message("not.a.polymorphic.lambda"))
    }
  })

  protected override def innerType(expectedType: Option[ScType]): TypeResult =
    polymorphicLambdaType().left.flatMap { _ =>
      // the referenced expression has no expected type
      val typeResult = referencedExpr.getNonValueType(expectedType = None)
      convertReferencedType(typeResult, ResolveMode.Full, expectedType)
    }

  override def multiType(mode: ResolveMode): Array[TypeResult] = {
    val polyLambdaType = polymorphicLambdaType()
    if (polyLambdaType.isLeft) {
      val typeResult: Array[TypeResult] = referencedExpr match {
        case ref: ScReferenceExpression => ref.multiType(mode)
        case expr                       => Array(expr.getNonValueType(expectedType = None))
      }
      typeResult.map(convertReferencedType(_, mode, this.expectedType()))
    } else Array(polyLambdaType)
  }

  override def multiResolve(mode: ResolveMode): Option[Array[ScalaResolveResult]] = {
    referencedExpr match {
      case ref: ScReferenceExpression => Some(ref.resolveWithMode(mode))
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
    gen:  ScGenericCall,
    tp:   ScType,
    mode: ResolveMode
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
      mode          = mode,
      withImplicits = true
    )

    workWithApplyCandidates(applyCandidates) match {
      case Some(tp) => tp
      case None     => Nothing(using gen.projectContext)
    }
  }
}
