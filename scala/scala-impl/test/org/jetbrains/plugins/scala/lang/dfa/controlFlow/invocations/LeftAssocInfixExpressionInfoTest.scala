package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.PassByValue

class LeftAssocInfixExpressionInfoTest extends InvocationInfoTestBase {

  def testBuiltinInfixExpressions(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |def main(): Int = {
         |${markerStart}23 + 16${markerEnd}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("16")
    val expectedMappedParamNames = List("") // mapped params in synthetic methods have empty names
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, expectedExpressionInText = "23")
  }

  def testCustomInfixExpressions(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |case class AndWrapper(wrapped: String) {
         |def &(rhs: String): String = wrapped + rhs
         |}
         |
         |def main(): String = {
         |val hello = AndWrapper("Hello ")
         |${markerStart}hello & "World"${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("\"World\"")
    val expectedMappedParamNames = List("rhs")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, expectedExpressionInText = "hello")
  }
}
