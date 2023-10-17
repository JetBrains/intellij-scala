package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import com.intellij.psi.CommonClassNames
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaStatementAnchor
import org.jetbrains.plugins.scala.lang.dfa.analysis.invocations.ScalaInvocationInstruction
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, ScalaDfaVariableDescriptor}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.PassByValue
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.ArgumentFactory.ArgumentCountLimit
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.{InvocationInfo, InvokedElement}
import org.jetbrains.plugins.scala.lang.dfa.utils.ScalaDfaConstants.SyntheticOperators.NumericBinary
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction

trait InvocationTransformation { this: ScalaDfaControlFlowBuilder =>
  final def transformInvocation(invocation: ScExpression, rreq: ResultReq, instanceQualifier: Option[ScalaDfaVariableDescriptor] = None): Unit = rreq.provideOne {
    val invocationsInfo = invocation match {
      case methodCall: ScMethodCall => InvocationInfo.fromMethodCall(methodCall)
      case methodInvocation: MethodInvocation => List(InvocationInfo.fromMethodInvocation(methodInvocation))
      case referenceExpression: ScReferenceExpression => List(InvocationInfo.fromReferenceExpression(referenceExpression))
      case constructorInvocation: ScNewTemplateDefinition => List(InvocationInfo.fromConstructorInvocation(constructorInvocation))
      case _ => Nil
    }

    if (invocationsInfo.isEmpty || isUnsupportedInvocation(invocation, invocationsInfo) ||
      invocationsInfo.exists(_.argListsInEvaluationOrder.flatten.size > ArgumentCountLimit)) {
      buildUnknownCall(invocation, 0, ResultReq.Required)
    } else if (!tryTransformIntoSpecialRepresentation(invocation, invocationsInfo)) {
      invocationsInfo.tail.foreach(invocationInfo => {
        transformMethodInvocation(invocation, invocationInfo, instanceQualifier)
        pop()
      })

      transformMethodInvocation(invocation, invocationsInfo.head, instanceQualifier)
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

  private def transformMethodInvocation(invocation: ScExpression,
                                        invocationInfo: InvocationInfo,
                                        instanceQualifier: Option[ScalaDfaVariableDescriptor]): Unit = {
    buildCollectionAccessAssertions(invocation, invocationInfo)

    val byValueArgs = invocationInfo.argListsInEvaluationOrder.flatMap(_.filter(_.passingMechanism == PassByValue))
    byValueArgs.foreach(arg => transformExpression(arg.content, ResultReq.Required))

    val transfer = maybeTransferValue(CommonClassNames.JAVA_LANG_THROWABLE)
    addInstruction(new ScalaInvocationInstruction(
      invocationInfo,
      ScalaStatementAnchor(invocation),
      instanceQualifier,
      transfer,
      analysedMethodInfo
    ))
  }

  private def tryTransformIntoSpecialRepresentation(invocation: ScExpression,
                                                    invocationsInfo: Seq[InvocationInfo]): Boolean = {
    if (invocationsInfo.size > 1) return false
    val invocationInfo = invocationsInfo.head

    invocationInfo.invokedElement match {
      case Some(InvokedElement(psiElement)) => psiElement match {
        case function: ScSyntheticFunction =>
          tryTransformSyntheticFunctionSpecially(function, invocationInfo, invocation)
        case _ => false
      }
      case _ => false
    }
  }
}
