package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.PassByValue

class ApplyAndUpdateInfoTest extends InvocationInfoTestBase {

  def testApplyMethods(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |case class SomeStringWrapper(wrapped: String)
         |
         |def main(): String = {
         |val somethingWrapped = ${markerStart}SomeStringWrapper("Wrap me")${markerEnd}
         |somethingWrapped.wrapped
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("\"Wrap me\"")
    val expectedMappedParamNames = List("wrapped")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
    verifyThisExpression(invocationInfo, "SomeStringWrapper")
  }
}
