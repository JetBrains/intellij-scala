package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.AssertionMatchers
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestAdapter, SharedTestProjectToken}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationExtractors.{extractExpressionFromArgument, extractInvocationUnderMarker}
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassingMechanism, ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScReferenceExpression}
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.junit.Assert.assertTrue

abstract class InvocationInfoTestBase extends ScalaLightCodeInsightFixtureTestAdapter with AssertionMatchers {

  override protected def sharedProjectToken: SharedTestProjectToken = SharedTestProjectToken(classOf[InvocationInfo])

  protected def markerStart: String = MarkersUtils.start()

  protected def markerEnd: String = MarkersUtils.end()

  protected def generateInvocationInfoFor(code: String): InvocationInfo = {
    val (codeWithoutMarkers, ranges) = MarkersUtils.extractNumberedMarkers(code.strip)
    val actualFile = configureFromFileText(codeWithoutMarkers)

    extractInvocationUnderMarker(actualFile, ranges) match {
      case methodInvocation: MethodInvocation => InvocationInfo.fromMethodInvocation(methodInvocation)
      case referenceExpression: ScReferenceExpression => InvocationInfo.fromReferenceExpression(referenceExpression)
    }
  }

  protected def verifyInvokedElement(invocationInfo: InvocationInfo, expectedText: String): Unit = {
    val actualText = invocationInfo.invokedElement.get.toString
    actualText shouldBe expectedText
  }

  protected def verifyArguments(invocationInfo: InvocationInfo, expectedArgCount: Int, expectedProperArgsInText: Seq[String],
                                expectedMappedParamNames: Seq[String], expectedPassingMechanisms: Seq[PassingMechanism],
                                isRightAssociative: Boolean = false): Unit = {
    val args = invocationInfo.argsInEvaluationOrder
    val properArgs = invocationInfo.properArguments

    args.size shouldBe expectedArgCount
    args.count(_.kind == ThisArgument) shouldBe 1
    convertArgsToText(properArgs) shouldBe expectedProperArgsInText
    properArgs.map(_.kind.asInstanceOf[ProperArgument].parameterMapping.name) shouldBe expectedMappedParamNames
    args.map(_.passingMechanism) shouldBe expectedPassingMechanisms

    if (isRightAssociative) {
      assertTrue("In a right associative call, the first argument should be a proper argument", args.head.kind.is[ProperArgument])
      args(1).kind shouldBe ThisArgument
      args.head +: args.tail.tail shouldBe properArgs
    } else {
      args.head.kind shouldBe ThisArgument
      args.tail shouldBe properArgs
    }
  }

  protected def verifyThisExpression(invocationInfo: InvocationInfo, expectedExpressionInText: String): Unit = {
    val thisArgument = invocationInfo.thisArgument.get
    val thisExpression = extractExpressionFromArgument(thisArgument)
    thisExpression.getText shouldBe expectedExpressionInText
  }

  private def convertArgsToText(args: Seq[Argument]): Seq[String] = args.map(extractExpressionFromArgument).map(_.getText)
}
