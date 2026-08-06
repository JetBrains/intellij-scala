package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.tests

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.PassByValue

class RightAssocInfixOperatorsInfoTest extends InvocationInfoTestBase {

  def testBuiltinRightAssocInfixOperators(): Unit = {
    val sugaredSyntax = "element :: someList"
    val desugaredSyntax = "someList.::(element)"

    val code = (invocationSyntax: String) =>
      s"""
         |def main(): Int = {
         |  val element = 22
         |  val someList = List(3, 9, 10)
         |  val newList = ${markerStart}${invocationSyntax}${markerEnd}
         |  newList.head
         |}
         |""".stripMargin

    for ((invocationSyntax, evaluationOrderReversed) <- List((sugaredSyntax, true), (desugaredSyntax, false))) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("element")
      val expectedMappedParamNames = List("x")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "List#::")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping,
        isRightAssociative = evaluationOrderReversed)
      verifyThisExpression(invocationInfo, "someList")
    }
  }

  def testCustomRightAssocInfixOperators(): Unit = {
    val sugaredSyntax = "\"World\" &: hello"
    val desugaredSyntax = "hello.&:(\"World\")"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  case class AndWrapper(wrapped: String) {
         |    def &:(other: String): String = wrapped + other
         |  }
         |
         |  def main(): String = {
         |    val hello = AndWrapper("Hello ")
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for ((invocationSyntax, evaluationOrderReversed) <- List((sugaredSyntax, true), (desugaredSyntax, false))) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("\"World\"")
      val expectedMappedParamNames = List("other")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "AndWrapper#&:")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping,
        isRightAssociative = evaluationOrderReversed)
      verifyThisExpression(invocationInfo, "hello")
    }
  }

  def testChainedRightAssocInfixOperators(): Unit = {
    val sugaredSyntax = "el1 :: 444 :: 2 :: el2 :: 0 :: someList"
    val desugaredSyntax = "someList.::(0).::(el2).::(2).::(444).::(el1)"

    val code = (invocationSyntax: String) =>
      s"""
         |def main(): Int = {
         |  val el1 = 7
         |  val el2 = 50
         |  val someList = List(2, 3, 8)
         |  val newList = ${markerStart}${invocationSyntax}${markerEnd}
         |  newList.head
         |}
         |""".stripMargin

    for ((invocationSyntax, sugared) <- List((sugaredSyntax, true), (desugaredSyntax, false))) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("el1")
      val expectedMappedParamNames = List("x")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "List#::")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping,
        isRightAssociative = sugared)
      verifyThisExpression(invocationInfo, if (sugared) "444 :: 2 :: el2 :: 0 :: someList"
      else "someList.::(0).::(el2).::(2).::(444)")
    }
  }
}
