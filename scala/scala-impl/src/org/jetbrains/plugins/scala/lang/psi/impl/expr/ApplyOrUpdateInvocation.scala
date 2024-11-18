package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi.PsiTypeParameterListOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignment, ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.getDynamicNameForMethodInvocation
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}

//data collected to resolve update/apply/dynamic calls
case class ApplyOrUpdateInvocation(
  argClauses:        List[Seq[ScExpression]],
  baseExpr:          ScExpression,
  targetType:        ScType,
  curriedTypeParams: Seq[TypeParameter],
  typeArgs:          Seq[ScTypeElement],
  expectedType:      () => Option[ScType],
  isDynamic:         Boolean,
  isUpdate:          Boolean
) {

  def collectCandidates(isShape: Boolean, withImplicits: Boolean = true): Array[ScalaResolveResult] = {
    val nameArgForDynamic = Option.when(isDynamic)(CommonNames.Apply)

    val processor =
      new MethodResolveProcessor(
        baseExpr,
        methodName,
        argClauses,
        typeArgs,
        curriedTypeParams,
        expectedOption    = expectedType,
        isShapeResolve    = isShape,
        enableTupling     = true,
        nameArgForDynamic = nameArgForDynamic
      )

    val simpleCandidates = candidatesFromType(processor, targetType)

    val candidates =
      if (simpleCandidates.forall(!_.isApplicable()) && withImplicits) {
        val noImplicitsForArgs = simpleCandidates.nonEmpty
        candidatesWithConversion(processor, noImplicitsForArgs)
      } else simpleCandidates

    candidates.toArray
  }

  private def candidatesWithConversion(
    processor:          MethodResolveProcessor,
    noImplicitsForArgs: Boolean
  ) = {
    processor.resetPrecedence()

    ImplicitConversionResolveResult.processImplicitConversionsAndExtensions(
      Option(processor.refName),
      baseExpr,
      processor,
      Option(targetType.inferValueType),
      noImplicitsForArgs,
      forCompletion = false
    ) {
      _.withImports.withType
    }(baseExpr)

    processor.candidatesS
  }

  private def methodName =
    if (isUpdate)       CommonNames.Update
    else if (isDynamic) getDynamicNameForMethodInvocation(argClauses.flatten)
    else                CommonNames.Apply

  private def candidatesFromType(processor: MethodResolveProcessor, fromType: ScType): Set[ScalaResolveResult] = {
    processor.processType(fromType, baseExpr, ScalaResolveState.withFromType(fromType))
    processor.candidatesS
  }
}

object ApplyOrUpdateInvocation {
  def apply(
    gen: ScGenericCall,
    tp: ScType,
    isDynamic: Boolean,
    stripTypeArgs: Boolean
  ): ApplyOrUpdateInvocation = {
    ApplyOrUpdateInvocation(
      List.empty,
      gen.referencedExpr,
      if (stripTypeArgs) unpackPolyType(tp)._1 else tp,
      Seq.empty,
      if (stripTypeArgs) Seq.empty else gen.typeArgs.typeArgs,
      () => gen.expectedType(),
      isDynamic = isDynamic,
      isUpdate = false
    )
  }

  def apply(
    call:           MethodInvocation,
    tp:             ScType,
    isDynamic:      Boolean,
    stripTypeArgs:  Boolean
  ): ApplyOrUpdateInvocation = {
    val argClauses = argumentClauses(call, isDynamic)

    val (baseExpr, baseTp, curriedTypeParams, typeArgs) =
      call.getEffectiveInvokedExpr match {
        case gen: ScGenericCall =>
          if (stripTypeArgs) (gen, unpackPolyType(tp)._1, Seq.empty, Seq.empty)
          else               (gen.referencedExpr, tp, Seq.empty, gen.arguments)
        case expression =>
          val (targetType, curried) = unpackPolyType(tp)
          (expression, targetType, curried, Seq.empty)
      }

    ApplyOrUpdateInvocation(
      argClauses,
      baseExpr,
      baseTp,
      curriedTypeParams,
      typeArgs,
      () => call.expectedType(),
      isDynamic,
      call.isUpdateCall
    )
  }

  private def unpackPolyType(tp: ScType): (ScType, Seq[TypeParameter]) = tp match {
    case ScTypePolymorphicType(internal, tparams) => (internal, tparams)
    case other                                    => (other, Seq.empty)
  }

  private def argumentClauses(call: MethodInvocation, isDynamic: Boolean): List[Seq[ScExpression]] = {
    import call.projectContext

    val newValueForUpdate = call.getContext match {
      case assign: ScAssignment if call.isUpdateCall =>
        val rightExpr = assign.rightExpression
          .getOrElse(createExpressionFromText("scala.Predef.???", call))
        Seq(rightExpr)
      case _ => Seq.empty
    }
    val arguments = call.argumentExpressions ++ newValueForUpdate

    if (!isDynamic) List(arguments)
    else {
      val emptyStringExpression = createExpressionFromText("\"\"", call)
      List(Seq(emptyStringExpression), arguments)
    }
  }

  def innerSrrHasTypeParameters(srr: ScalaResolveResult): Boolean =
    srr.name == CommonNames.Apply && srr.innerResolveResult.exists {
      _.element match {
        case tpo: PsiTypeParameterListOwner => tpo.getTypeParameters.nonEmpty
        case tpo: ScTypeParametersOwner     => tpo.typeParameters.nonEmpty
        case _                              => false
      }
    }
}
