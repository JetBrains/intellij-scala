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

  def testMultipleParameterListsWithNamedParameters(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object SomeObject {
         |  def manyParamLists(a: Int, b: => Int)(c: Int)(d: Int, e: => Int, f: Boolean)(g: Int = 100): Int = 55
         |
         |  def main(): Int = {
         |    ${markerStart}manyParamLists(b = 99, a = 4)(15)(4, f = true, e = 9)(g = 100)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = List(1 + 2, 1, 3, 1)
    val expectedProperArgsInText = List(List("99", "4"), List("15"), List("4", "true", "9"), List("100"))
    val expectedMappedParamNames = List(List("b", "a"), List("c"), List("d", "f", "e"), List("g"))
    val expectedPassingMechanisms = List(List(PassByValue, PassByName, PassByValue), List(PassByValue),
      List(PassByValue, PassByValue, PassByName), List(PassByValue))

    verifyInvokedElement(invocationInfo, "SomeObject#manyParamLists")
    verifyArgumentsWithMultipleArgLists(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
  }

  def testMultipleArgumentListsWithDefaultParameters(): Unit = {
    val sugaredSyntax = "manyParamLists(4, 99)(15)(4)()"
    val desugaredSyntax = "manyParamLists(4, 99)(15)(4, 9, true)(100)"

    val code = (invocationSyntax: String) =>
      s"""
         |object SomeObject {
         |  def manyParamLists(a: Int, b: => Int)(c: Int)(d: Int, e: => Int = 9, f: Boolean = true)(g: Int = 100): Int = 55
         |
         |  def main(): Int = {
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = List(1 + 2, 1, 3, 1)
      val expectedProperArgsInText = List(List("4", "99"), List("15"), List("4", "9", "true"), List("100"))
      val expectedMappedParamNames = List(List("a", "b"), List("c"), List("d", "e", "f"), List("g"))
      val expectedPassingMechanisms = List(List(PassByValue, PassByValue, PassByName), List(PassByValue),
        List(PassByValue, PassByName, PassByValue), List(PassByValue))

      verifyInvokedElement(invocationInfo, "SomeObject#manyParamLists")
      verifyArgumentsWithMultipleArgLists(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
    }
  }

  def testMultipleArgumentListsWithInfixOperators(): Unit = {
    val sugaredSyntax = "(obj1 ++++ obj2)(5, 9)(obj2.x, 333)"
    val desugaredSyntax = "obj1.++++(obj2)(5, 9)(obj2.x, 333)"

    val code = (invocationSyntax: String) =>
      s"""
         |case class Something(x: Int) {
         |  def ++++(other: Something)(a: Int, b: Int)(c: Int, d: Int): Int = a * c + b * d - x
         |}
         |
         |  def main(): Int = {
         |    val obj1 = Something(3)
         |    val obj2 = Something(6)
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = List(1 + 1, 2, 2)
      val expectedProperArgsInText = List(List("obj2"), List("5", "9"), List("obj2.x", "333"))
      val expectedMappedParamNames = List(List("other"), List("a", "b"), List("c", "d"))
      val expectedPassingMechanisms = (1 to 3).map(_ => List(PassByValue, PassByValue)).toList

      verifyInvokedElement(invocationInfo, "Something#++++")
      verifyArgumentsWithMultipleArgLists(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "obj1")
    }
  }

  def testMultipleArgumentListsWithRightAssociativeInfixOperators(): Unit = {
    val sugaredSyntax = "(obj1 ++++: obj2)(5, 9)(obj2.x, 333)"
    val desugaredSyntax = "obj2.++++:(obj1)(5, 9)(obj2.x, 333)"

    val code = (invocationSyntax: String) =>
      s"""
         |case class Something(x: Int) {
         |  def ++++:(other: Something)(a: Int, b: Int)(c: Int, d: Int): Int = a * c + b * d - x
         |}
         |
         |  def main(): Int = {
         |    val obj1 = Something(3)
         |    val obj2 = Something(6)
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for ((invocationSyntax, evaluationOrderReversed) <- List((sugaredSyntax, true), (desugaredSyntax, false))) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = List(1 + 1, 2, 2)
      val expectedProperArgsInText = List(List("obj1"), List("5", "9"), List("obj2.x", "333"))
      val expectedMappedParamNames = List(List("other"), List("a", "b"), List("c", "d"))
      val expectedPassingMechanisms = (1 to 3).map(_ => List(PassByValue, PassByValue)).toList

      verifyInvokedElement(invocationInfo, "Something#++++:")
      verifyArgumentsWithMultipleArgLists(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, isRightAssociative = evaluationOrderReversed)
      verifyThisExpression(invocationInfo, "obj2")
    }
  }

  def testMultipleParameterListsWithVarargs(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object SomeObject {
         |  def withVarargs(a: Int, b: => Int)(c: Int)(d: Int, e: => Boolean, f: Int*)(g: Int = 100): Int = 55
         |
         |  def main(): Int = {
         |    val x = -2
         |    ${markerStart}withVarargs(b = 99, a = 4)(15)(4, true, 8, 333, x, 23 * 10000)(100)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = List(1 + 2, 1, 3, 1)
    val expectedProperArgsInText = List(List("99", "4"), List("15"),
      List("4", "true", "8 :: 333 :: x :: 23 * 10000 :: Nil: _*"), List("100"))
    val expectedMappedParamNames = List(List("b", "a"), List("c"), List("d", "e", "f"), List("g"))
    val expectedPassingMechanisms = List(List(PassByValue, PassByName, PassByValue), List(PassByValue),
      List(PassByValue, PassByName, PassByValue), List(PassByValue))

    verifyInvokedElement(invocationInfo, "SomeObject#withVarargs")
    verifyArgumentsWithMultipleArgLists(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
  }
}
