package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.tests

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfoTestBase
import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.arguments.Argument.{PassByName, PassByValue}

class DefaultAndImplicitParamsInfoTest extends InvocationInfoTestBase {

  def testDefaultParameters(): Unit = {
    val sugaredSyntax = "someMethod(4, 9)"
    val desugaredSyntax = "someMethod(4, 9, 5)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def someMethod(x: Int, y: => Int, z: Int = 5): Int = x + 2 * y + z
         |
         |  def main(): Int = {
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 3
      val expectedProperArgsInText = List("4", "9", "5")
      val expectedMappedParamNames = List("x", "y", "z")
      val expectedPassingMechanisms = List(PassByValue, PassByValue, PassByName, PassByValue)
      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList

      verifyInvokedElement(invocationInfo, "Test#someMethod")
      verifyArgumentsWithSingleArgList(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
    }
  }

  //  TODO matchedParameters doesn't return implicit parameters in the mapping, left for later
  //  def testImplicitParameters(): Unit = {
  //    val sugaredSyntax = s"implicit val something: Int = 15; ${markerStart}aMethod${markerEnd}"
  //    val desugaredSyntax = s"val something = 15; ${markerStart}aMethod(something)${markerEnd}"
  //
  //    val code = (invocationSyntaxWithMarkers: String) =>
  //      s"""
  //         |object SomeObject {
  //         |  def aMethod(implicit x: Int): Int = x + 2
  //         |
  //         |  def main(): Int = {
  //         |    ${invocationSyntaxWithMarkers}
  //         |  }
  //         |}
  //         |""".stripMargin
  //
  //    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
  //      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))
  //
  //      val expectedArgCount = 1 + 1
  //      val expectedProperArgsInText = List("something")
  //      val expectedMappedParamNames = List("x")
  //      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue).toList
  //      val expectedParamToArgMapping = (0 until expectedArgCount - 1).toList
  //
  //      verifyInvokedElement(invocationInfo, "SomeObject#aMethod")
  //      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
  //        expectedMappedParamNames, expectedPassingMechanisms, expectedParamToArgMapping)
  //    }
  //  }
}
