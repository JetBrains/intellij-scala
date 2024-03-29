package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.tests

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.PassByValue

class ParameterlessAndArgumentlessInfoTest extends InvocationInfoTestBase {

  def testCustomMethodsWithoutParentheses(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |  case class MyClass(wrapped: Int) {
         |    def someMethod: Int = wrapped + 5
         |  }
         |
         |  def main(): Int = {
         |    val myObj = MyClass(6)
         |    ${markerStart}myObj.someMethod${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 0
    val expectedProperArgsInText = Nil
    val expectedMappedParamNames = Nil
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
    val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

    verifyInvokedElement(invocationInfo, "MyClass#someMethod")
    verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
    verifyThisExpression(invocationInfo, "myObj")
  }

  def testCustomParameterlessMethodsWithParentheses(): Unit = {
    val unparenthesizedSyntax = "myObj.someMethod"
    val parenthesizedSyntax = "myObj.someMethod()"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  case class MyClass(wrapped: Int) {
         |    def someMethod(): Int = wrapped + 5
         |  }
         |
         |  def main(): Int = {
         |    val myObj = MyClass(6)
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(unparenthesizedSyntax, parenthesizedSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 0
      val expectedProperArgsInText = Nil
      val expectedMappedParamNames = Nil
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "MyClass#someMethod")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
      verifyThisExpression(invocationInfo, "myObj")
    }
  }

  def testBuiltinParameterlessMethodsWithParentheses(): Unit = {
    val unparenthesizedSyntax = "myArray.clear()"
    val parenthesizedSyntax = "myArray.clear()"

    val code = (invocationSyntax: String) =>
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |
         |object Test {
         |  def main(): Int = {
         |    val myArray = ArrayBuffer(3, 8, 5)
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(unparenthesizedSyntax, parenthesizedSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 0
      val expectedProperArgsInText = Nil
      val expectedMappedParamNames = Nil
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "ArrayBuffer#clear")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
      verifyThisExpression(invocationInfo, "myArray")
    }
  }

  def testBuiltinMethodsWithoutParentheses(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |def main(): Int = {
         |  val myList = List(5, 0, 4, 4, 4, 4)
         |  ${markerStart}myList.size${markerEnd}
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 0
    val expectedProperArgsInText = Nil
    val expectedMappedParamNames = Nil
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
    val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

    verifyInvokedElement(invocationInfo, "SeqLike#size")
    verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
    verifyThisExpression(invocationInfo, "myList")
  }
}
