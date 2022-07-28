package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignment, ScExpression, ScGenericCall}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.{conformsToDynamic, getDynamicNameForMethodInvocation}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}

//data collected to resolve update/apply/dynamic calls
case class ApplyOrUpdateInvocation(call: MethodInvocation,
                                   argClauses: List[Seq[Expression]],
                                   baseExpr: ScExpression,
                                   baseExprType: ScType,
                                   typeArgs: Seq[ScTypeElement],
                                   typeParams: Seq[TypeParameter],
                                   isDynamic: Boolean) {

  def collectCandidates(isShape: Boolean): Array[ScalaResolveResult] = {
    val nameArgForDynamic = if (isDynamic) Some("apply") else None

    val processor = new MethodResolveProcessor(baseExpr, methodName, argClauses, typeArgs, typeParams,
      isShapeResolve = isShape, enableTupling = true, nameArgForDynamic = nameArgForDynamic)

    val simpleCandidates: Set[ScalaResolveResult] = candidatesNoImplicit(processor)

    if (isDynamic) return simpleCandidates.toArray

    val candidates =
      if (simpleCandidates.forall(!_.isApplicable())) {
        val noImplicitsForArgs = simpleCandidates.nonEmpty
        candidatesWithConversion(processor, noImplicitsForArgs)
      }
      else simpleCandidates

    candidates.toArray
  }

  private def candidatesNoImplicit(processor: MethodResolveProcessor): Set[ScalaResolveResult] = {
    val fromPolymorphicType = baseExprType match {
      case ScTypePolymorphicType(_: ScMethodType | _: UndefinedType, _) =>
        Set.empty
      case ScTypePolymorphicType(internal, typeParam) if typeParam.nonEmpty && shouldProcess(internal) =>
        processType(processor, internal)
      case _ =>
        Set.empty
    }

    val baseValueType = baseExprType.inferValueType

    if (fromPolymorphicType.isEmpty && shouldProcess(baseValueType))
      processType(processor, baseValueType)
    else
      Set.empty
  }

  private def candidatesWithConversion(processor: MethodResolveProcessor, noImplicitsForArgs: Boolean) = {
    processor.resetPrecedence()
    ImplicitConversionResolveResult.processImplicitConversionsAndExtensions(Some(processor.refName), call, processor, noImplicitsForArgs) {
      _.withImports.withType
    }(baseExpr)
    processor.candidatesS
  }

  private def methodName =
    if (isDynamic) getDynamicNameForMethodInvocation(call.argumentExpressions)
    else if (call.isUpdateCall) "update"
    else "apply"

  private def shouldProcess(fromType: ScType): Boolean =
    !isDynamic || conformsToDynamic(fromType, call.resolveScope)

  private def processType(processor: MethodResolveProcessor, fromType: ScType): Set[ScalaResolveResult] = {
    processor.processType(fromType, call.getEffectiveInvokedExpr, ScalaResolveState.withFromType(fromType))
    processor.candidatesS
  }
}

object ApplyOrUpdateInvocation {

  def apply(call: MethodInvocation, tp: ScType, isDynamic: Boolean): ApplyOrUpdateInvocation = {
    val argClauses = argumentClauses(call, isDynamic)

    val typeParams = call.getInvokedExpr.getNonValueType().toOption.collect {
      case ScTypePolymorphicType(_, tps) => tps
    }.getOrElse(Seq.empty)

    val (baseExpr, baseExprType, typeArgs: Seq[ScTypeElement]) = call.getEffectiveInvokedExpr match {
      case gen: ScGenericCall =>
        // The type arguments are for the apply/update method, separate them from the referenced expression. (SCL-3489)
        val referencedType = gen.referencedExpr.getNonValueType().getOrNothing
        referencedType match {
          case _: ScTypePolymorphicType => //that means that generic call is important here
            (gen, gen.`type`().getOrNothing, Seq.empty)
          case _ =>
            (gen.referencedExpr, gen.referencedExpr.`type`().getOrNothing, gen.arguments)
        }
      case expression => (expression, tp, Seq.empty)
    }

    ApplyOrUpdateInvocation(call, argClauses, baseExpr, baseExprType, typeArgs, typeParams, isDynamic)
  }

  private def argumentClauses(call: MethodInvocation, isDynamic: Boolean): List[Seq[Expression]] = {
    import call.projectContext

    val newValueForUpdate = call.getContext match {
      case assign: ScAssignment if call.isUpdateCall =>
        val rightExpr = assign.rightExpression
          .getOrElse(createExpressionFromText("scala.Predef.???")) //we can't to not add something => add Nothing expression
        Seq(rightExpr)
      case _ =>
        Seq.empty
    }
    val arguments = call.argumentExpressions ++ newValueForUpdate

    if (!isDynamic) List(arguments)
    else {
      val emptyStringExpression = createExpressionFromText("\"\"")
      List(Seq(emptyStringExpression), arguments)
    }
  }
}
