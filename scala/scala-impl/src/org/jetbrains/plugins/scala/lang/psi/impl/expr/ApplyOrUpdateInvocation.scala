package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignment, ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.{conformsToDynamic, getDynamicNameForMethodInvocation}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}

//data collected to resolve update/apply/dynamic calls
case class ApplyOrUpdateInvocation(
  argClauses:   List[Seq[ScExpression]],
  baseExpr:     ScExpression,
  baseExprType: ScType,
  typeArgs:     Seq[ScTypeElement],
  expectedType: () => Option[ScType],
  isDynamic:    Boolean,
  isUpdate:     Boolean
) {

  def collectCandidates(isShape: Boolean, withImplicits: Boolean = true): Array[ScalaResolveResult] = {
    val nameArgForDynamic = Option.when(isDynamic)(CommonNames.Apply)

    val processor =
      new MethodResolveProcessor(
        baseExpr,
        methodName,
        argClauses,
        typeArgs,
        Seq.empty,
        expectedOption    = expectedType,
        isShapeResolve    = isShape,
        enableTupling     = true,
        nameArgForDynamic = nameArgForDynamic
      )

    val simpleCandidates = candidatesNoImplicit(processor)

    if (isDynamic) simpleCandidates.toArray
    else {
      val candidates =
        if (simpleCandidates.forall(!_.isApplicable()) && withImplicits) {
          val noImplicitsForArgs = simpleCandidates.nonEmpty
          candidatesWithConversion(processor, noImplicitsForArgs)
        } else simpleCandidates

      candidates.toArray
    }
  }

  private def candidatesNoImplicit(processor: MethodResolveProcessor): Set[ScalaResolveResult] =
    baseExprType match {
      case ScTypePolymorphicType(_: ScMethodType | _: UndefinedType, _) =>
        Set.empty
      case ScTypePolymorphicType(internal, tparams) if shouldProcess(internal) =>
        val cands = candidatesFromType(processor, internal)
        cands.map(_.copy(unresolvedTypeParameters = tparams.toOption))
      case other =>
        if (shouldProcess(other)) candidatesFromType(processor, other)
        else                      Set.empty
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
      Option(baseExprType.inferValueType),
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

  private def shouldProcess(fromType: ScType): Boolean =
    !isDynamic || conformsToDynamic(fromType, baseExpr.resolveScope)

  private def candidatesFromType(processor: MethodResolveProcessor, fromType: ScType): Set[ScalaResolveResult] = {
    processor.processType(fromType, baseExpr, ScalaResolveState.withFromType(fromType))
    processor.candidatesS
  }
}

object ApplyOrUpdateInvocation {
  def apply(gen: ScGenericCall, tp: ScType, stripTypeArgs: Boolean): ApplyOrUpdateInvocation =
    ApplyOrUpdateInvocation(
      List.empty,
      gen.referencedExpr,
      if (stripTypeArgs) unpackPolyType(tp) else tp,
      if (stripTypeArgs) Seq.empty          else gen.typeArgs.typeArgs,
      () => gen.expectedType(),
      isDynamic = false,
      isUpdate = false
    )

  def apply(
    call:           MethodInvocation,
    tp:             ScType,
    isDynamic:      Boolean,
    stripTypeArgs:  Boolean
  ): ApplyOrUpdateInvocation = {
    val argClauses = argumentClauses(call, isDynamic)

    val (baseExpr, baseTp, typeArgs) =
      call.getEffectiveInvokedExpr match {
        case gen: ScGenericCall =>
          if (stripTypeArgs) (gen, unpackPolyType(tp), Seq.empty)
          else               (gen.referencedExpr, tp, gen.arguments)
        case expression => (expression, tp, Seq.empty)
      }

    ApplyOrUpdateInvocation(
      argClauses,
      baseExpr,
      baseTp,
      typeArgs,
      () => call.expectedType(),
      isDynamic,
      call.isUpdateCall
    )
  }

  private def unpackPolyType(tp: ScType): ScType = tp match {
    case tpt: ScTypePolymorphicType => tpt.internalType
    case other                      => other
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
}
