package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitConversionResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.resolve.processor.DynamicResolveProcessor.getDynamicNameForMethodInvocation
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor.InvocationClause
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, ScalaResolveState}
import org.jetbrains.plugins.scala.project.ProjectContext

//data collected to resolve update/apply/dynamic calls
case class ApplyOrUpdateInvocation(
  argClauses:        Seq[InvocationClause],
  baseExpr:          PsiElement,
  targetType:        ScType,
  curriedTypeParams: Seq[TypeParameter],
  expectedType:      () => Option[ScType],
  isDynamic:         Boolean,
  isUpdate:          Boolean
) {

  def collectCandidates(isShape: Boolean, withImplicits: Boolean = true): Array[ScalaResolveResult] = {
    val nameArgForDynamic = Option.when(isDynamic)(CommonNames.Apply)

    val proc = new MethodResolveProcessor(
      baseExpr,
      methodName,
      argClauses,
      curriedTypeParams,
      expectedOption    = expectedType,
      isShapeResolve    = isShape,
      enableTupling     = true,
      nameArgForDynamic = nameArgForDynamic
    )

    val simpleCandidates = candidatesFromType(proc, targetType)

    val candidates =
      if (simpleCandidates.forall(!_.isApplicable()) && withImplicits) {
        val noImplicitsForArgs = simpleCandidates.nonEmpty
        candidatesWithConversion(proc, noImplicitsForArgs)
      } else simpleCandidates

    candidates.toArray
  }

  private def candidatesWithConversion(
    processor:          MethodResolveProcessor,
    noImplicitsForArgs: Boolean
  ): Set[ScalaResolveResult] = {
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
    if (isUpdate) CommonNames.Update
    else if (isDynamic) {
      val valueArgs = argClauses.collect { case InvocationClause(_, Some(args)) => args }.flatten
      getDynamicNameForMethodInvocation(valueArgs)
    } else CommonNames.Apply

  private def candidatesFromType(processor: MethodResolveProcessor, fromType: ScType): Set[ScalaResolveResult] = {
    processor.processType(fromType, baseExpr, ScalaResolveState.withFromType(fromType))
    processor.candidatesS
  }
}

object ApplyOrUpdateInvocation {
  def apply(
    call:      PsiElement,
    tp:        ScType,
    isDynamic: Boolean,
  ): Option[ApplyOrUpdateInvocation] = {
    val isUpdateCall = call.getContext match {
      case inv: MethodInvocation => inv.isUpdateCall
      case _                     => false
    }

    val contextInfo                     = MethodResolveProcessor.getInvocationInfo(call, call)
    val (targetType, curriedTypeParams) = unpackPolyType(tp)

    Option.when(contextInfo.invocationClauses.nonEmpty) {

      val updatedClauses =
        updateClauseForUpdateOrDynamic(
          call,
          isDynamic,
          isUpdateCall,
          contextInfo.invocationClauses.head
        )

      ApplyOrUpdateInvocation(
        updatedClauses ++ contextInfo.invocationClauses.tail,
        call,
        targetType,
        curriedTypeParams,
        contextInfo.expectedType,
        isDynamic,
        isUpdateCall
      )
    }
  }

  private def unpackPolyType(tp: ScType): (ScType, Seq[TypeParameter]) = tp match {
    case ScTypePolymorphicType(internal, tparams) => (internal, tparams)
    case other                                    => (other, Seq.empty)
  }

  private def updateClauseForUpdateOrDynamic(
    call:         PsiElement,
    isDynamic:    Boolean,
    isUpdateCall: Boolean,
    clause:       InvocationClause
  ): Seq[InvocationClause] = {
    implicit val projectContext: ProjectContext = call

    val newValueForUpdate = call.getContext match {
      case assign: ScAssignment if isUpdateCall =>
        val rightExpr = assign.rightExpression
          .getOrElse(createExpressionFromText("scala.Predef.???", call))

        Option(Seq(rightExpr))
      case _ => None
    }

    val arguments = clause.args match {
      case Some(args) => newValueForUpdate match {
        case Some(argsForUpdate) => (args ++ argsForUpdate).toOption
        case None                => args.toOption
      }
      case None => newValueForUpdate
    }

    if (!isDynamic) Seq(clause.copy(args = arguments))
    else {
      //if the first (non-synthetic) clause has type arguments, they must go along with the
      //synthetic "method name" argument. Otherwise, in scala 2 we might miss errors/fail in overloaded contexts.
      val typeArguments         = clause.targs
      val emptyStringExpression = createExpressionFromText("\"\"", call)

      val syntheticClause =
        InvocationClause(
          targs = typeArguments,
          args  = Seq(emptyStringExpression).toOption
        )

      //see comment above
      val modifiedClause =
        if (typeArguments.isEmpty)     clause.toOption
        else if (clause.args.nonEmpty) clause.copy(targs = None).toOption
        else                           None //If the original clause was type-args-only, discard it altogether

      Seq(syntheticClause) ++ modifiedClause
    }
  }

  def innerSrrHasTypeParameters(srr: ScalaResolveResult): Boolean =
    srr.name == CommonNames.Apply &&
      srr.innerResolveResult.fold(false)(_.elementHasTypeParameters)
}
