package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.{PassingMechanism, ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ExpressionTransformer
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertTrue

import scala.reflect.ClassTag


abstract class InvocationInfoTestBase extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[InvocationInfo])

  protected def markerStart: String = MarkersUtils.start()

  protected def markerEnd: String = MarkersUtils.end()

  protected def generateInvocationInfoFor(code: String): InvocationInfo = {
    extractInvocationUnderMarker(code) match {
      case methodInvocation: MethodInvocation => InvocationInfo.fromMethodInvocation(methodInvocation)
      case referenceExpression: ScReferenceExpression => InvocationInfo.fromReferenceExpression(referenceExpression)
    }
  }

  protected def verifyInvokedElement(invocationInfo: InvocationInfo, expectedText: String): Unit = {
    val actualText = invocationInfo.invokedElement.get match {
      case definition: ScFunctionDefinition => s"${definition.containingClass.name}#${definition.name}"
      case synthetic: ScSyntheticFunction => s"$synthetic: ${synthetic.name}"
      case _ => throw new IllegalArgumentException(s"Invoked element of unknown type: ${invocationInfo.invokedElement}")
    }

    actualText shouldBe expectedText
  }

  protected def verifyArguments(invocationInfo: InvocationInfo, expectedArgCount: Int, expectedProperArgsInText: Seq[String],
                                expectedMappedParamNames: Seq[String], expectedPassingMechanisms: Seq[PassingMechanism]): Unit = {
    val args = invocationInfo.argsInEvaluationOrder

    args.size shouldBe expectedArgCount
    args.head.kind shouldBe ThisArgument
    assertTrue("All arguments except the first one should be proper arguments", args.tail.forall(_.kind.is[ProperArgument]))

    val actualMappedParamNames = args.tail.map(_.kind.asInstanceOf[ProperArgument].parameterMapping.name)
    actualMappedParamNames shouldBe expectedMappedParamNames
    convertArgsToText(args.tail) shouldBe expectedProperArgsInText
    args.map(_.passingMechanism) shouldBe expectedPassingMechanisms
  }

  protected def verifyThisExpression(invocationInfo: InvocationInfo, expectedExpressionInText: String): Unit = {
    val thisExpression = extractExpressionFromArgument(invocationInfo.argsInEvaluationOrder.head)
    thisExpression.getText shouldBe expectedExpressionInText
  }

  private def convertArgsToText(args: Seq[Argument]): Seq[String] = {
    args.map(extractExpressionFromArgument)
      .map(_.getText)
  }

  private def extractExpressionFromArgument(argument: Argument): ScExpression = argument.content match {
    case expressionTransformer: ExpressionTransformer => expressionTransformer.expression
    case _ => throw new IllegalArgumentException(s"Argument is not an expression: $argument")
  }

  private def extractElementOfType[A <: PsiElement : ClassTag](actualFile: PsiFile, ranges: Seq[TextRange]): A = {
    val range = ranges.head
    val start = range.getStartOffset
    val end = range.getEndOffset
    val runtimeClass = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[A]]

    if (start == end) PsiTreeUtil.getNonStrictParentOfType(actualFile.findElementAt(start), runtimeClass)
    else PsiTreeUtil.findElementOfClassAtRange(actualFile, start, end, runtimeClass)
  }

  private def extractInvocationUnderMarker(code: String): ScExpression = {
    val (codeWithoutMarkers, ranges) = MarkersUtils.extractNumberedMarkers(code.strip)
    val actualFile = configureFromFileText(codeWithoutMarkers)

    val methodInvocationUnderMarker = extractElementOfType[MethodInvocation](actualFile, ranges)
    val referenceExpressionUnderMarker = extractElementOfType[ScReferenceExpression](actualFile, ranges)

    Option(methodInvocationUnderMarker)
      .orElse(Option(referenceExpressionUnderMarker))
      .getOrElse(throw new IllegalArgumentException("There is no invocation under the marker"))
  }
}
