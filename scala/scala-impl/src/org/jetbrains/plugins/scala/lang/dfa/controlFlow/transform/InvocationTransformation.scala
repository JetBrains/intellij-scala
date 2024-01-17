package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder.StackValue
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ArgumentFactory.ArgumentCountLimit
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.SyntheticOperators.NumericBinary
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

trait InvocationTransformation { this: ScalaDfaControlFlowBuilder =>
  final def transformInvocation(invocation: ScExpression, rreq: ResultReq, instanceQualifier: Option[ScalaDfaVariableDescriptor] = None): rreq.Result = rreq.result {
    val invocationsInfos = invocation match {
      case methodCall: ScMethodCall => InvocationInfo.fromMethodCall(methodCall)
      case methodInvocation: MethodInvocation => List(InvocationInfo.fromMethodInvocation(methodInvocation))
      case referenceExpression: ScReferenceExpression => List(InvocationInfo.fromReferenceExpression(referenceExpression))
      case constructorInvocation: ScNewTemplateDefinition => List(InvocationInfo.fromConstructorInvocation(constructorInvocation))
      case _ => Nil
    }

    if (invocationsInfos.isEmpty || isUnsupportedInvocation(invocation, invocationsInfos) ||
      invocationsInfos.exists(_.argListsInEvaluationOrder.flatten.size > ArgumentCountLimit)) {
      buildUnknownCall(ResultReq.Required)
    } else{
      tryTransformIntoSpecialRepresentation(invocationsInfos)
        .getOrElse {
          invocationsInfos.tail.foreach(invocationInfo => {
            val result = transformMethodInvocation(invocationInfo, instanceQualifier)
            pop(result)
          })

          transformMethodInvocation(invocationsInfos.head, instanceQualifier)
        }
    }
  }

  private def isUnsupportedInvocation(invocation: ScExpression, invocationsInfo: Seq[InvocationInfo]): Boolean = {
    val unsupportedExpression = invocation match {
      case infix: ScInfixExpr => isUnsupportedInfixSyntheticAssignment(infix.operation.getText)
      case _ => false
    }

    val unsupportedInvokedElement = invocationsInfo
      .flatMap(_.invokedElement)
      .flatMap(_.simpleName)
      .exists(startsWithUnsupportedMethodName)

    startsWithUnsupportedMethodName(invocation.getText) || unsupportedExpression || unsupportedInvokedElement
  }

  private def startsWithUnsupportedMethodName(name: String): Boolean = {
    name.startsWith("assert") || name.startsWith("require") || name.startsWith("getClass")
  }

  private def isUnsupportedInfixSyntheticAssignment(operation: String): Boolean = {
    // There were significant problems with recognizing and transforming properly combinations of synthetic methods
    // with var assignments, for example x += 3. Supporting it most likely needs modifications to relevant PSI elements.
    val isBinaryModifyingAssignment = operation.length == 2 && operation(1) == '=' &&
      operation != "==" && operation != "!="
    val isUnsupportedSyntheticOperator = NumericBinary.keys.toList.contains(operation(0).toString) ||
      operation(0) == '&' || operation(0) == '|'
    isBinaryModifyingAssignment && isUnsupportedSyntheticOperator
  }

  private def transformMethodInvocation(invocationInfo: InvocationInfo,
                                        instanceQualifier: Option[ScalaDfaVariableDescriptor]): StackValue = {
    buildCollectionAccessAssertions(invocationInfo)

    val args = invocationInfo.argumentExpressionsInEvaluationOrder.map(transformExpression(_, ResultReq.Required))
    invoke(args, invocationInfo, instanceQualifier, analysedMethodInfo)
  }

  private def tryTransformIntoSpecialRepresentation(invocationsInfo: Seq[InvocationInfo]): Option[StackValue] =
    invocationsInfo match {
      case Seq(invocationInfo) =>
        invocationInfo.invokedElement match {
          case Some(InvokedElement(psiElement)) => psiElement match {
            case function: ScSyntheticFunction => tryTransformSyntheticFunctionSpecially(function, invocationInfo)
            case _ => None
          }
          case _ => None
        }
      case _ => None
    }

  final def transformImplicitConversionInvocation(e: PsiNamedElement, expr: ScExpression): StackValue = {
    e.asOptionOf[ScFunction].flatMap(InvocationInfo.tryFromImplicitConversion(_, expr)) match {
      case Some(invocationInfo) =>
        val args = invocationInfo.argumentExpressionsInEvaluationOrder.map(transformExpressionBeforeConversion(_, ResultReq.Required))
        invoke(args, invocationInfo, None, analysedMethodInfo)
      case _ =>
        unsupported(TransformationFailedException(expr, s"Cannot handle transformation of implicit conversion targeting ${e.name}")) {
          val arg = transformExpressionBeforeConversion(expr, ResultReq.Required)
          buildUnknownCall(ResultReq.Required, Seq(arg))
        }
    }
  }
}
