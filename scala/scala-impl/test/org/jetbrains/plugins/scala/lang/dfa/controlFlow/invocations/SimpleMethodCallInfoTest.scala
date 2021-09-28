package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.{PassByName, PassByValue}

class SimpleMethodCallInfoTest extends InvocationInfoTestBase {

  def testSimpleMethodCall(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |def simpleFun(firstArg: Int, secondArg: Boolean, thirdArg: String, fourthArg: Int): Int = {
         |firstArg + fourthArg
         |}
         |
         |def main(): Int = {
         |${markerStart}simpleFun(3 + 8, 5 > 9, "Hello", 9 * 4 - 2)${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 4 // implicit "this" argument
    val expectedProperArgsInText = List("3 + 8", "5 > 9", "\"Hello\"", "9 * 4 - 2")
    val expectedMappedParamNames = List("firstArg", "secondArg", "thirdArg", "fourthArg")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "SomeClass#simpleFun")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
  }

  def testByNameArguments(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class AnotherClass {
         |def funWithByNames(arg0: Int, arg1: => Boolean, arg2: String, arg3: => Int): Int = {
         |firstArg + fourthArg
         |}
         |
         |def main(): Int = {
         |val x = 3
         |${markerStart}funWithByNames(328944 * 22, 5 >= 3 && false, "Hello", -3324 + x)${markerEnd}
         |}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 4
    val expectedProperArgsInText = List("328944 * 22", "5 >= 3 && false", "\"Hello\"", "-3324 + x")
    val expectedMappedParamNames = List("arg0", "arg1", "arg2", "arg3")
    val expectedPassingMechanisms = List(PassByValue, PassByValue, PassByName, PassByValue, PassByName)

    verifyInvokedElement(invocationInfo, "AnotherClass#funWithByNames")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
  }
}
