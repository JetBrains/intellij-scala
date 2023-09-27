package org.jetbrains.plugins.scala.lang.dfa.invocationInfo

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, StringExt}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationExtractors.{extractInvocationUnderMarker, forceExtractExpressionFromArgument}
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{PassingMechanism, ProperArgument, ThisArgument}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScMethodCall, ScNewTemplateDefinition, ScReferenceExpression}
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers
import org.junit.Assert.assertTrue

abstract class InvocationInfoTestBase extends ScalaLightCodeInsightFixtureTestCase with AssertionMatchers {

  protected def markerStart: String = MarkersUtils.start()

  protected def markerEnd: String = MarkersUtils.end()

  protected def generateInvocationInfoFor(code: String, assertSingleInvocation: Boolean = true): InvocationInfo = {
    val (codeWithoutMarkers, ranges) = MarkersUtils.extractNumberedMarkers(code.strip.withNormalizedSeparator)
    val actualFile = configureFromFileText(codeWithoutMarkers)

    extractInvocationUnderMarker(actualFile, ranges) match {
      case methodCall: ScMethodCall => val invocationsInfo = InvocationInfo.fromMethodCall(methodCall)
        if (assertSingleInvocation) invocationsInfo.size shouldBe 1
        invocationsInfo.head
      case methodInvocation: MethodInvocation => InvocationInfo.fromMethodInvocation(methodInvocation)
      case referenceExpression: ScReferenceExpression => InvocationInfo.fromReferenceExpression(referenceExpression)
      case newTemplateDefinition: ScNewTemplateDefinition => InvocationInfo.fromConstructorInvocation(newTemplateDefinition)
    }
  }

  protected def verifyInvokedElement(invocationInfo: InvocationInfo, expectedText: String): Unit = {
    val actualText = invocationInfo.invokedElement.get.toString
    actualText shouldBe expectedText
  }

  protected def verifyArgumentsWithSingleArgList(invocationInfo: InvocationInfo, expectedArgCount: Int,
                                                 expectedProperArgsInText: List[String],
                                                 expectedMappedParamNames: List[String],
                                                 expectedPassingMechanisms: List[PassingMechanism],
                                                 expectedParamToArgMapping: List[Int],
                                                 isRightAssociative: Boolean = false): Unit = {
    invocationInfo.argListsInEvaluationOrder.size shouldBe 1
    val args = invocationInfo.argListsInEvaluationOrder.head
    val properArgs = invocationInfo.properArguments.flatten

    args.size shouldBe expectedArgCount
    args.count(_.kind == ThisArgument) shouldBe 1
    convertArgsToText(properArgs) shouldBe expectedProperArgsInText
    properArgs.map(_.kind.asInstanceOf[ProperArgument].parameterMapping.name) shouldBe expectedMappedParamNames
    args.map(_.passingMechanism) shouldBe expectedPassingMechanisms
    invocationInfo.paramToProperArgMapping.map(_.get) shouldBe expectedParamToArgMapping

    if (isRightAssociative) {
      assertTrue("In a right associative call, the first argument should be a proper argument", args.head.kind.is[ProperArgument])
      args(1).kind shouldBe ThisArgument
      args.head +: args.tail.tail shouldBe properArgs
    } else {
      args.head.kind shouldBe ThisArgument
      args.tail shouldBe properArgs
    }
  }

  protected def verifyArgumentsWithMultipleArgLists(invocationInfo: InvocationInfo, expectedArgCount: List[Int],
                                                    expectedProperArgsInText: List[List[String]],
                                                    expectedMappedParamNames: List[List[String]],
                                                    expectedPassingMechanisms: List[List[PassingMechanism]],
                                                    expectedParamToArgMapping: List[Int],
                                                    isRightAssociative: Boolean = false): Unit = {
    invocationInfo.argListsInEvaluationOrder.size shouldBe expectedArgCount.size
    val args = invocationInfo.argListsInEvaluationOrder
    val properArgs = invocationInfo.properArguments

    args.map(_.size) shouldBe expectedArgCount
    args.head.count(_.kind == ThisArgument) shouldBe 1
    assertTrue("\"This\" argument should only be present in the first argument list",
      args.tail.forall(_.count(_.kind == ThisArgument) == 0))
    properArgs.map(convertArgsToText) shouldBe expectedProperArgsInText
    properArgs.map(_.map(_.kind.asInstanceOf[ProperArgument].parameterMapping.name)) shouldBe expectedMappedParamNames
    args.map(_.map(_.passingMechanism)) shouldBe expectedPassingMechanisms
    invocationInfo.paramToProperArgMapping.map(_.get) shouldBe expectedParamToArgMapping

    if (isRightAssociative) {
      assertTrue("In a right associative call, the first argument should be a proper argument",
        args.head.head.kind.is[ProperArgument])
      args.head(1).kind shouldBe ThisArgument
    } else {
      args.head.head.kind shouldBe ThisArgument
    }
  }

  protected def verifyArgumentsInInvalidInvocation(invocationInfo: InvocationInfo, expectedArgCount: Int,
                                                   expectedProperArgsInText: List[String]): Unit = {
    invocationInfo.argListsInEvaluationOrder.size shouldBe 1
    val args = invocationInfo.argListsInEvaluationOrder.head
    val properArgs = invocationInfo.properArguments.flatten

    args.size shouldBe expectedArgCount
    args.count(_.kind == ThisArgument) shouldBe 1
    convertArgsToText(properArgs) shouldBe expectedProperArgsInText

    args.head.kind shouldBe ThisArgument
    args.tail shouldBe properArgs
  }

  protected def verifyThisExpression(invocationInfo: InvocationInfo, expectedExpressionInText: String): Unit = {
    val thisArgument = invocationInfo.thisArgument.get
    val thisExpression = forceExtractExpressionFromArgument(thisArgument)
    thisExpression.getText shouldBe expectedExpressionInText
  }

  private def convertArgsToText(args: List[Argument]): List[String] = args.map(forceExtractExpressionFromArgument).map(_.getText)
}
