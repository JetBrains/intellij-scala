package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.PassByValue

class ClassConstructorInfoTest extends InvocationInfoTestBase {

  def testConstructorCalls(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |  class Something(firstArg: Int, secondArg: Boolean)
         |
         |  def main(): Int = {
         |    val something = ${markerStart}new Something(3 + 8, 5 > 9)${markerEnd}
         |    3
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 2
    val expectedProperArgsInText = List("3 + 8", "5 > 9")
    val expectedMappedParamNames = List("firstArg", "secondArg")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
    val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

    verifyInvokedElement(invocationInfo, "Something#Something")
    verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  }
}
