package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.PassByValue

class AssignmentSugarsInfoTest extends InvocationInfoTestBase {

  def testCustomUpdateMethods(): Unit = {
    val sugaredSyntax = "collection(4) = 12"
    val desugaredSyntax = "collection.update(4, 12)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  class MyMutableCollection {
         |    val arr = ArrayBuffer[Int](1, 2, 3, 4, 5, 6, 7)
         |    def update(position: Int, value: Int): Unit = arr.insert(position, value * 2)
         |  }
         |
         |  def main(): String = {
         |    val collection = new MyMutableCollection
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 2
      val expectedProperArgsInText = List("4", "12")
      val expectedMappedParamNames = List("position", "value")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "MyMutableCollection#update")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "collection")
    }
  }

  def testComplexCustomUpdateMethods(): Unit = {
    val sugaredSyntax = "collection(4, \"dddddx\", 7 <= 8 && 9 > 4 * 2) = 12"
    val desugaredSyntax = "collection.update(4, \"dddddx\", 7 <= 8 && 9 > 4 * 2, 12)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  class MyMutableCollection {
         |    val arr = ArrayBuffer[Int](1, 2, 3, 4, 5, 6, 7)
         |    def foo(x: Int) = x + 3
         |
         |    def update(position1: Int, position2: String, position3: Boolean, value: Int): Unit = {
         |      if (position3) arr.insert(position1, value * 2)
         |      else arr.insert(position1, value * 7
         |    }
         |  }
         |
         |  def main(): String = {
         |    val collection = new MyMutableCollection
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 4
      val expectedProperArgsInText = List("4", "\"dddddx\"", "7 <= 8 && 9 > 4 * 2", "12")
      val expectedMappedParamNames = List("position1", "position2", "position3", "value")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "MyMutableCollection#update")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "collection")
    }
  }

  def testBuiltinUpdateMethods(): Unit = {
    val sugaredSyntax = "someMutableArray(3) = 15 + 2 * 9"
    val desugaredSyntax = "someMutableArray.update(3, 15 + 2 * 9)"

    val code = (invocationSyntax: String) =>
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |
         |object Test {
         |  def main(): String = {
         |    val someMutableArray = ArrayBuffer(11339, 9 * 40, 9 - 4 - 4 - 4, -15)
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 2
      val expectedProperArgsInText = List("3", "15 + 2 * 9")
      val expectedMappedParamNames = List("idx", "elem")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "ResizableArray#update")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "someMutableArray")
    }
  }
}
