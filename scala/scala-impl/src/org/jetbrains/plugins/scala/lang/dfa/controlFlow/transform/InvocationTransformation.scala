package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.InstructionBuilder.StackValue
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.PassByValue
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ArgumentFactory.ArgumentCountLimit
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.SyntheticOperators.NumericBinary
import org.jetbrains.plugins.scala.lang.psi.api.expr._
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

    val unsupportedInvokedElement = invocationsInfo.flatMap(_.invokedElement).flatMap(_.simpleName)
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

  def transformMethodInvocation(invocationInfo: InvocationInfo,
                                instanceQualifier: Option[ScalaDfaVariableDescriptor]): StackValue = {
    buildCollectionAccessAssertions(invocationInfo)

    val byValueArgs = invocationInfo.argListsInEvaluationOrder.flatMap(_.filter(_.passingMechanism == PassByValue))
    val args = byValueArgs.map(arg => transformExpression(arg.content, ResultReq.Required))

    invoke(
      args,
      invocationInfo,
      instanceQualifier,
      analysedMethodInfo
    )
  }

  private def tryTransformIntoSpecialRepresentation(invocationsInfo: Seq[InvocationInfo]): Option[StackValue] = {
    if (invocationsInfo.size > 1) {
      None
    } else {
      val invocationInfo = invocationsInfo.head

      invocationInfo.invokedElement match {
        case Some(InvokedElement(psiElement)) => psiElement match {
          case function: ScSyntheticFunction => tryTransformSyntheticFunctionSpecially(function, invocationInfo)
          case _ => None
        }
        case _ => None
      }
    }
  }
}
