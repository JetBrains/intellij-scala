package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.PassByValue

class PrefixAndPostfixOperatorsInfoTest extends InvocationInfoTestBase {

  def testBuiltinPostfixOperators(): Unit = {
    val sugaredSyntax = "myList head"
    val desugaredSyntax = "myList.head"

    val code = (invocationSyntax: String) =>
      s"""
         |def main(): Int = {
         |  val myList = List("elem1", "elem3", "elem5", "", "elem6")
         |  ${markerStart}${invocationSyntax}${markerEnd}
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 0
      val expectedProperArgsInText = Nil
      val expectedMappedParamNames = Nil
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "IterableLike#head")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "myList")
    }
  }

  def testCustomPostfixOperators(): Unit = {
    val sugaredSyntax = "myObject >>#"
    val desugaredSyntax = "myObject.>>#"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  case class MyObject(x: Int) {
         |    def >># : Int = x + 3
         |  }
         |
         |  def main(): Int = {
         |    val myObject = MyObject(6)
         |    val x = ${markerStart}${invocationSyntax}${markerEnd}
         |    if (x == 5) x else x + 2
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 0
      val expectedProperArgsInText = Nil
      val expectedMappedParamNames = Nil
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "MyObject#>>#")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "myObject")
    }
  }

  def testBuiltinPrefixOperators(): Unit = {
    val sugaredSyntax = "!condition"
    val desugaredSyntax = "condition.unary_!"

    val code = (invocationSyntax: String) =>
      s"""
         |def main(): Int = {
         |  val condition = 3 + 2 >= 4 || 2 == 3
         |  ${markerStart}${invocationSyntax}${markerEnd}
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 0
      val expectedProperArgsInText = Nil
      val expectedMappedParamNames = Nil
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "Synthetic method: unary_!")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "condition")
    }
  }

  def testCustomPrefixOperators(): Unit = {
    val sugaredSyntax = "!myObject"
    val desugaredSyntax = "myObject.unary_!"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  case class MyObject(x: Int) {
         |    def unary_! : Boolean = x > 100
         |  }
         |
         |  def main(): Int = {
         |    val myObject = MyObject(52)
         |    val b = ${markerStart}${invocationSyntax}${markerEnd}
         |    if (b) 3 else 5
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 0
      val expectedProperArgsInText = Nil
      val expectedMappedParamNames = Nil
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "MyObject#unary_!")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "myObject")
    }
  }
}
