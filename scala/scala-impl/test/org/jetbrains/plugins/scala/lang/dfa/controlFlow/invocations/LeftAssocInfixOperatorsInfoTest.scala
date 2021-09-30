package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.arguments.Argument.{PassByName, PassByValue}

class LeftAssocInfixOperatorsInfoTest extends InvocationInfoTestBase {

  def testBuiltinInfixOperators(): Unit = {
    val sugaredSyntax = "23 + 16"
    val desugaredSyntax = "23.+(16)"

    val code = (invocationSyntax: String) =>
      s"""
         |def main(): Int = {
         |  ${markerStart}${invocationSyntax}${markerEnd}
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("16")
      val expectedMappedParamNames = List("") // mapped params in synthetic methods have empty names
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "Synthetic method: +")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "23")
    }
  }

  def testCustomInfixOperators(): Unit = {
    val sugaredSyntax = "hello & \"World\""
    val desugaredSyntax = "hello.&(\"World\")"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  case class AndWrapper(wrapped: String) {
         |    def &(rhs: String): String = wrapped + rhs
         |  }
         |
         |  def main(): String = {
         |    val hello = AndWrapper("Hello ")
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("\"World\"")
      val expectedMappedParamNames = List("rhs")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "AndWrapper#&")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "hello")
    }
  }

  def testLogicalOperators(): Unit = {
    val sugaredSyntax = "predicate && otherPredicate"
    val desugaredSyntax = "predicate.&&(otherPredicate)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def main(): Boolean = {
         |    val predicate = 5 > 3 && 12 == 11
         |    val otherPredicate = 333 > 10
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("otherPredicate")
      val expectedMappedParamNames = List("")
      val expectedPassingMechanisms = List(PassByValue, PassByName)

      verifyInvokedElement(invocationInfo, "Synthetic method: &&")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "predicate")
    }
  }
}
