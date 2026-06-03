package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.tests

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.PassByValue

class NamedParametersInfoTest extends InvocationInfoTestBase {

  def testNamedParameters(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |  def simpleFun(firstArg: Int, secondArg: Int, thirdArg: Int, fourthArg: Int, fifthArg: Int, sixthArg: Int): Int = {
         |    firstArg + fourthArg * fifthArg
         |  }
         |
         |  def main(): Int = {
         |    ${markerStart}simpleFun(9999, 30, fifthArg = 93939, thirdArg = 15, sixthArg = -10, fourthArg = 33 + 2 * 9)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 6
    val expectedProperArgsInText = List("9999", "30", "93939", "15", "-10", "33 + 2 * 9")
    val expectedMappedParamNames = List("firstArg", "secondArg", "fifthArg", "thirdArg", "sixthArg", "fourthArg")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
    val expectedParamToArgMapping = List(0, 1, 3, 5, 2, 4)

    verifyInvokedElement(invocationInfo, "SomeClass#simpleFun")
    verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  }
}
