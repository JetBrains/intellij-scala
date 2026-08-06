package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.tests

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.PassByValue

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
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "MyMutableCollection#update")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
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
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "MyMutableCollection#update")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
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
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "ResizableArray#update")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
      verifyThisExpression(invocationInfo, "someMutableArray")
    }
  }

  // TODO later, setters don't work for a similar reason to += etc., hard to separate the calls or misleading resolving

  //  def testCustomSetterMethods(): Unit = {
  //    val sugaredSyntax = "obj.property = 12"
  //    val desugaredSyntax = "obj.property_=(12)"
  //
  //    val code = (invocationSyntax: String) =>
  //      s"""
  //         |object Test {
  //         |    class Something {
  //         |      private var _property: Int = 14
  //         |
  //         |      def property: Int = _property
  //         |      def property_= (newValue: Int): Unit = _property = 2 * newValue + 4
  //         |    }
  //         |
  //         |    def main(): Int = {
  //         |      val obj = new Something
  //         |      ${markerStart}${invocationSyntax}${markerEnd}
  //         |      3
  //         |    }
  //         |}
  //         |""".stripMargin
  //
  //    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
  //      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))
  //
  //      val expectedArgCount = 1 + 1
  //      val expectedProperArgsInText = List("12")
  //      val expectedMappedParamNames = List("newValue")
  //      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
  //      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList
  //
  //      verifyInvokedElement(invocationInfo, "Something#property_=")
  //      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
  //        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  //      verifyThisExpression(invocationInfo, "obj")
  //    }
  //  }
  //
  //  def testGeneratedSetterMethods(): Unit = {
  //    val sugaredSyntax = "obj.property = 12"
  //    val desugaredSyntax = "obj.property_=(12)"
  //
  //    val code = (invocationSyntax: String) =>
  //      s"""
  //         |object Test {
  //         |  class Something {
  //         |    var property = 14
  //         |  }
  //         |
  //         |  def main(): Int = {
  //         |    val obj = new Something
  //         |    ${markerStart}${invocationSyntax}${markerEnd}
  //         |    3
  //         |  }
  //         |}
  //         |""".stripMargin
  //
  //    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
  //      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))
  //
  //      val expectedArgCount = 1 + 1
  //      val expectedProperArgsInText = List("12")
  //      val expectedMappedParamNames = List("") // fake PSI method with no parameters
  //      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
  //      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList
  //
  //      verifyInvokedElement(invocationInfo, "Something#property_=")
  //      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
  //        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  //      verifyThisExpression(invocationInfo, "obj")
  //    }
  //  }

  def testOperatorEqualsAsSugaredAssignment(): Unit = {
    val sugaredSyntax = s"${markerStart}sth ^^= 15${markerEnd}"
    val desugaredSyntax1 = s"sth = ${markerStart}sth ^^ 15${markerEnd}"
    val desugaredSyntax2 = s"sth = ${markerStart}sth.^^(15)${markerEnd}"

    val code = (invocationSyntaxWithMarkers: String) =>
      s"""
         |object Test {
         |  case class Something(x: Int) {
         |    def ^^(y: Int) = Something(x * y + 2)
         |  }
         |
         |  def main(): Int = {
         |    var sth = Something(5)
         |    ${invocationSyntaxWithMarkers}
         |    sth.x
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax1, desugaredSyntax2)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("15")
      val expectedMappedParamNames = List("y")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "Something#^^")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
      verifyThisExpression(invocationInfo, "sth")
    }
  }

  def testOperatorEqualsAsSimpleMethodCall(): Unit = {
    val sugaredSyntax = "sth ^^= 15"
    val desugaredSyntax = "sth.^^=(15)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  case class Something(x: Int) {
         |    def ^^=(y: Int) = Something(x * y + 2)
         |  }
         |
         |  def main(): Int = {
         |    var sth = Something(5)
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |    sth.x
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("15")
      val expectedMappedParamNames = List("y")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "Something#^^=")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
      verifyThisExpression(invocationInfo, "sth")
    }
  }

  // TODO requires changes in the PSI tree to get update and setter subcalls

  //  def testOperatorEqualsCombinedWithUpdateAndApply(): Unit = {
  //    val sugaredSyntaxMarkingApply = s"${markerStart}sth(3) += 15${markerEnd}"
  //    val desugaredSyntaxMarkingApply = s"sth(3) = ${markerStart}sth(3)${markerEnd} + 15"
  //    val sugaredSyntaxMarkingOperator = s"${markerStart}sth(3) += 15${markerEnd}"
  //    val desugaredSyntaxMarkingOperator = s"sth(3) = ${markerStart}sth(3) + 15${markerEnd}"
  //
  //    val code = (invocationSyntaxWithMarkers: String) =>
  //      s"""
  //         |object Test {
  //         |  case class Something(var x: Int) {
  //         |    def apply(a: Int): Int = 3
  //         |    def update(a: Int, b: Int): Unit = x += a * b
  //         |  }
  //         |
  //         |  def main(): Int = {
  //         |    val sth = Something(5)
  //         |    ${invocationSyntaxWithMarkers}
  //         |    sth.x
  //         |  }
  //         |}
  //         |""".stripMargin
  //
  //    for (invocationSyntax <- List(sugaredSyntaxMarkingApply, desugaredSyntaxMarkingApply)) {
  //      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))
  //
  //      val expectedArgCount = 1 + 1
  //      val expectedProperArgsInText = List("3")
  //      val expectedMappedParamNames = List("a")
  //      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
  //
  //      verifyInvokedElement(invocationInfo, "Something#apply")
  //      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
  //        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  //      verifyThisExpression(invocationInfo, "sth")
  //    }
  //
  //    for (invocationSyntax <- List(sugaredSyntaxMarkingOperator, desugaredSyntaxMarkingOperator)) {
  //      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))
  //
  //      val expectedArgCount = 1 + 1
  //      val expectedProperArgsInText = List("15")
  //      val expectedMappedParamNames = List("")
  //      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
  //
  //      verifyInvokedElement(invocationInfo, "Synthetic method: +")
  //      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
  //        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  //      verifyThisExpression(invocationInfo, "sth(3)")
  //    }
  //  }
  //
  //  def testOperatorEqualsCombinedWithSetter(): Unit = {
  //    val sugaredSyntaxMarkingApply = s"${markerStart}sth.prop += xx${markerEnd}"
  //    val desugaredSyntaxMarkingApply = s"sth.prop = ${markerStart}sth.prop + xx${markerEnd}"
  //
  //    val code = (invocationSyntaxWithMarkers: String) =>
  //      s"""
  //         |object Test {
  //         |    case class Something(wrapped: Int) {
  //         |      def prop: Int = 3
  //         |      def prop_=(other: Int): Unit = prop = other * 2
  //         |    }
  //         |
  //         |    def main(): Int = {
  //         |      val sth = Something(5)
  //         |      val xx = 33
  //         |      ${invocationSyntaxWithMarkers}
  //         |      sth.prop
  //         |    }
  //         |  }
  //         |""".stripMargin
  //
  //    for (invocationSyntax <- List(sugaredSyntaxMarkingApply, desugaredSyntaxMarkingApply)) {
  //      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))
  //
  //      val expectedArgCount = 1 + 1
  //      val expectedProperArgsInText = List("xx")
  //      val expectedMappedParamNames = List("")
  //      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
  //      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList
  //
  //      verifyInvokedElement(invocationInfo, "Synthetic method: +")
  //      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
  //        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  //      verifyThisExpression(invocationInfo, "sth.prop")
  //    }
  //  }
}
