package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.tests

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{PassByName, PassByValue}

class ClassConstructorInfoTest extends InvocationInfoTestBase {

  def testMultipleConstructorClausesWithDefaults(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class Something(first: Int)(second: => Int, third: Int = 3)
         |val something = ${markerStart}new Something(1)(2)${markerEnd}
         |""".stripMargin
    }

    verifyInvokedElement(invocationInfo, "Something#Something")
    verifyArgumentsWithMultipleArgLists(invocationInfo,
      expectedArgCount = List(2, 2),
      expectedProperArgsInText = List(List("1"), List("2", "<no-expr>")),
      expectedMappedParamNames = List(List("first"), List("second", "third")),
      expectedPassingMechanisms = List(List(PassByValue, PassByValue), List(PassByName, PassByValue)),
      expectedParamToArgMapping = List(0, 1, 2))
  }

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
