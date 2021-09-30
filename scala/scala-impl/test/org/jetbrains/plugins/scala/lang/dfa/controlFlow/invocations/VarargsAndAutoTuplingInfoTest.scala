package org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.invocations.Argument.PassByValue

class VarargsAndAutoTuplingInfoTest extends InvocationInfoTestBase {

  def testAutoTupling(): Unit = {
    val sugaredSyntax = "someMethod(7, 21 % 8, 50000)"
    val desugaredSyntax = "someMethod((7, 21 % 8, 50000))"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def someMethod(x: (Int, Int, Int)): Int = x._1 + x._2 * x._3
         |
         |  def main(): Int = {
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("(7, 21 % 8, 50000)")
      val expectedMappedParamNames = List("x")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "Test#someMethod")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
    }
  }

  def testAutoTuplingWithGenericParams(): Unit = {
    val sugaredSyntax = "someMethod(7, 21 % 8, 50000)"
    val desugaredSyntax = "someMethod((7, 21 % 8, 50000))"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def someMethod[T](x: T): Int = 3
         |
         |  def main(): Int = {
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("(7, 21 % 8, 50000)")
      val expectedMappedParamNames = List("x")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "Test#someMethod")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
    }
  }

  def testAutoTuplingWithGenericParamsAndEmptyTuple(): Unit = {
    val sugaredSyntax = "someMethod()"
    val desugaredSyntax = "someMethod(())"

    val code = (invocationSyntax: String) =>
      s"""
         |object Test {
         |  def someMethod[T](x: T): Int = 3
         |
         |  def main(): Int = {
         |    ${markerStart}${invocationSyntax}${markerEnd}
         |  }
         |}
         |""".stripMargin

    for (invocationSyntax <- List(sugaredSyntax, desugaredSyntax)) {
      val invocationInfo = generateInvocationInfoFor(code(invocationSyntax))

      val expectedArgCount = 1 + 1
      val expectedProperArgsInText = List("()")
      val expectedMappedParamNames = List("x")
      val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

      verifyInvokedElement(invocationInfo, "Test#someMethod")
      verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
        expectedMappedParamNames, expectedPassingMechanisms)
    }
  }

  def testVarargsStandardCall(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |  def someMethod(x: Int*): Int = x.sum
         |
         |  def main(): Int = {
         |    ${markerStart}someMethod(7, 21 % 8, 50000)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("7 :: 21 % 8 :: 50000 :: Nil: _*")
    val expectedMappedParamNames = List("x")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "Test#someMethod")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
  }

  def testVarargsWithSplatOperator(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |object Test {
         |  def someMethod(x: Int*): Int = x.sum
         |
         |  def main(): Int = {
         |    ${markerStart}someMethod(Seq(7, 21 % 8, 50000): _*)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 1
    val expectedProperArgsInText = List("Seq(7, 21 % 8, 50000): _*")
    val expectedMappedParamNames = List("x")
    val expectedPassingMechanisms = (1 to expectedArgCount).map(_ => PassByValue)

    verifyInvokedElement(invocationInfo, "Test#someMethod")
    verifyArguments(invocationInfo, expectedArgCount, expectedProperArgsInText,
      expectedMappedParamNames, expectedPassingMechanisms)
  }
}