package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ExpressionTransformer
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScExpression}
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertTrue


class InvocationInfoTestBase extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[InvocationInfo])

  protected def markerStart: String = MarkersUtils.start()

  protected def markerEnd: String = MarkersUtils.end()

  protected def generateInvocationInfoFor(code: String): InvocationInfo = {
    val invocation = extractInvocationUnderMarker(code)
    InvocationInfo.fromMethodInvocation(invocation)
  }

  protected def convertArgsToText(args: Seq[Argument]): Seq[String] = {
    args.map(extractExpressionFromArgument)
      .map(_.getText)
  }

  protected def extractExpressionFromArgument(argument: Argument): ScExpression = argument.content match {
    case expressionTransformer: ExpressionTransformer => expressionTransformer.expression
    case _ => throw new IllegalArgumentException(s"Argument is not an expression: $argument")
  }

  private def extractInvocationUnderMarker(code: String): MethodInvocation = {
    val (codeWithoutMarkers, ranges) = MarkersUtils.extractNumberedMarkers(code.strip)
    val actualFile = configureFromFileText(codeWithoutMarkers)

    val range = ranges.head
    val start = range.getStartOffset
    val end = range.getEndOffset

    val elementUnderMarker =
      if (start == end) PsiTreeUtil.getNonStrictParentOfType(actualFile.findElementAt(start), classOf[MethodInvocation])
      else PsiTreeUtil.findElementOfClassAtRange(actualFile, start, end, classOf[MethodInvocation])

    assertTrue("There should be a method invocation under the marker", elementUnderMarker != null)
    elementUnderMarker
  }
}
