package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.PassByValue

class ApplyMethodsInfoTest extends InvocationInfoTestBase {

  def testGeneratedFactoryApplyMethods(): Unit = {
    val sugaredSyntax = "SomeStringWrapper(\"Wrap me\")"
    val desugaredSyntax = "SomeStringWrapper.apply(\"Wrap me\")"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  case class SomeStringWrapper(wrapped: String)
         |
         |  def main(): String = {
         |    val somethingWrapped = ${markerStart}${invocationSyntax}${markerEnd}
         |    somethingWrapped.wrapped
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("\"Wrap me\"")
      val expectedMappedParamNames = List("wrapped")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "SomeStringWrapper#apply")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "SomeStringWrapper")
    }
  }

  def testCustomApplyMethodsInSingletonObjects(): Unit = {
    val sugaredSyntax = "SomeClass(4)"
    val desugaredSyntax = "SomeClass.apply(4)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  object SomeClass {
         |    def apply(x: Int): Int = 2 * x + 3
         |  }
         |
         |  def main(): String = {
         |    val x = ${markerStart}${invocationSyntax}${markerEnd}
         |    x + 2
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("4")
      val expectedMappedParamNames = List("x")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "SomeClass#apply")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "SomeClass")
    }
  }

  def testCustomApplyMethodsOnInstances(): Unit = {
    val sugaredSyntax = "obj(5)"
    val desugaredSyntax = "obj.apply(5)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  class SomeClass(y: Int) {
         |    def apply(x: Int): Int = 2 * x + y
         |  }
         |
         |  def main(): String = {
         |    val obj = new SomeClass(33)
         |    val x = ${markerStart}${invocationSyntax}${markerEnd}
         |    x + 3
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("5")
      val expectedMappedParamNames = List("x")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "SomeClass#apply")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "obj")
    }
  }

  def testBuiltinFactoryApplyMethodsWithFewArgs(): Unit = {
    val sugaredSyntax = "List(1113, 8 * 15)"
    val desugaredSyntax = "List.apply(1113, 8 * 15)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def main(): String = {
         |    val someList = ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 2
      val expectedProperArgsInText = List("1113", "8 * 15")
      val expectedMappedParamNames = List("xs", "xs")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "List#apply")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "List")
    }
  }

  def testBuiltinFactoryApplyMethodsWithMoreArgs(): Unit = {
    val sugaredSyntax = "List(1113, 8 * 15, 24, 9, 32992, 9, someFunc(33), 44, 47858, 45555, 6 - 6, 323, 44)"
    val desugaredSyntax = "List.apply(1113, 8 * 15, 24, 9, 32992, 9, someFunc(33), 44, 47858, 45555, 6 - 6, 323, 44)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def someFunc(x: Int): Int = x + 3
         |
         |  def main(): Unit = {
         |    val someList = ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 13
      val expectedProperArgsInText = List("1113", "8 * 15", "24", "9", "32992", "9", "someFunc(33)", "44",
        "47858", "45555", "6 - 6", "323", "44")
      val expectedMappedParamNames = (1 until expectedArgCount).map(_ => "xs")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "List#apply")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "List")
    }
  }

  def testBuiltinAccessorApplyMethods(): Unit = {
    val sugaredSyntax = "someSet(120)"
    val desugaredSyntax = "someSet.apply(120)"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def main(): String = {
         |    val someSet = Set(13, 13, 13, 1113, 8 * 15, 24, 9, 32992, 9, 33)
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("120")
      val expectedMappedParamNames = List("elem")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "GenSetLike#apply")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
      verifyThisExpression(invocationInfo, "someSet")
    }
  }
}
