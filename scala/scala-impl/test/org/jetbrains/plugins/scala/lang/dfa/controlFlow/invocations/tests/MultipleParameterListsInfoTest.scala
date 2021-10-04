package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByName, PassByValue}

class MultipleParameterListsInfoTest extends InvocationInfoTestBase {

  def testBasicCallsWithMultipleArgumentLists(): Unit = {
    val sugaredSyntax = "manyParamLists(4, 99)(15)(4, 9, true) { \"Hi\" }"
    val desugaredSyntax = "manyParamLists(4, 99)(15)(4, 9, true)(\"Hi\")"

    val code = (invocationSyntax: String) =>
      s"""
         |object SomeObject {
         |  def manyParamLists(a: Int, b: => Int)(c: Int)(d: Int, e: => Int, f: Boolean)(g: String): Int = 55
         |
         |  def main(): Int = {
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for ((invocationSyntax, lastArgText) <- List((sugaredSyntax, "{ \"Hi\" }"), (desugaredSyntax, "\"Hi\""))) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = List(1 + 2, 1, 3, 1)
      val expectedProperArgsInText = List(List("4", "99"), List("15"), List("4", "9", "true"), List(lastArgText))
      val expectedMappedParamNames = List(List("a", "b"), List("c"), List("d", "e", "f"), List("g"))
      val expectedPassingMechanisms = List(List(PassByValue, PassByValue, PassByName), List(PassByValue),
        List(PassByValue, PassByName, PassByValue), List(PassByValue))

      verifyInvokedElement(invocationInfo, "SomeObject#manyParamLists")
      verifyArgumentsWithMultipleArgLists(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
    }
  }

  // TODO tests for combinations with all of the previous ones: default, named, implicits params, right associativity etc.


  // TODO later, most likely requires changes in the PSI tree

  //  def testAutoTuplingInMultipleArgumentLists(): Unit = {
  //    val sugaredSyntax = "manyParamLists(4, 99)(15)(4, 9, true)(\"Hi\")"
  //    val desugaredSyntax = "manyParamLists(4, 99)(15)((4, 9, true))(\"Hi\")"
  //
  //    val code = (invocationSyntax: String) =>
  //      s"""
  //         |object SomeObject {
  //         |  def manyParamLists(a: Int, b: => Int)(c: Int)(d: (Int, Int, Boolean))(e: String): Int = 55
  //         |
  //         |  def main(): Int = {
  //         |    ${markerStart}${invocationSyntax}${markerEnd}
  //         |  }
  //         |}
  //         |""".stripMargin
  //
  //    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
  //      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))
  //
  //      val expectedArgCount = List(1 + 2, 1, 1, 1)
  //      val expectedProperArgsInText = List(List("4", "99"), List("15"), List("(4, 9, true)"), List("\"Hi\""))
  //      val expectedMappedParamNames = List(List("a", "b"), List("c"), List("d"), List("e"))
  //      val expectedPassingMechanisms = List(List(PassByValue, PassByValue, PassByName), List(PassByValue),
  //        List(PassByValue), List(PassByValue))
  //
  //      verifyInvokedElement(invocationInfo, "SomeObject#manyParamLists")
  //      verifyArgumentsWithMultipleArgLists(invocationInfo, expectedArgCount, expectedProperArgsInText,
  //        expectedMappedParamNames, expectedPassingMechanisms)
  //    }
  //  }
}