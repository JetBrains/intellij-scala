package org.jetbrains.plugins.scala.lang.dfa.invocationInfo.tests

import org.jetbrains.plugins.scala.lang.dfa.invocationInfo.InvocationInfoTestBase

class InvalidInvocationsInfoTest extends InvocationInfoTestBase {

  def testCallToNonExistentMethod(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |  def simpleFun(firstArg: Int, secondArg: Boolean, thirdArg: String, fourthArg: Int): Int = {
         |    firstArg + fourthArg
         |  }
         |
         |  def main(): Int = {
         |    ${markerStart}simpleFunn(3 + 8, 5 > 9, "Hello", 9 * 4 - 2)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 4
    val expectedProperArgsInText = List("3 + 8", "5 > 9", "\"Hello\"", "9 * 4 - 2")

    invocationInfo.invokedElement shouldBe None
    verifyArgumentsInInvalidInvocation(invocationInfo, expectedArgCount, expectedProperArgsInText)
  }

  def testCallWithTooFewArguments(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |  def simpleFun(firstArg: Int, secondArg: Boolean, thirdArg: String, fourthArg: Int): Int = {
         |    firstArg + fourthArg
         |  }
         |
         |  def main(): Int = {
         |    ${markerStart}simpleFun(3 + 8, 5 > 9, "Hello")${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 3
    val expectedProperArgsInText = List("3 + 8", "5 > 9", "\"Hello\"")

    invocationInfo.invokedElement shouldBe None
    verifyArgumentsInInvalidInvocation(invocationInfo, expectedArgCount, expectedProperArgsInText)
  }

  def testCallWithTooManyArguments(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |  def simpleFun(firstArg: Int, secondArg: Boolean, thirdArg: String, fourthArg: Int): Int = {
         |    firstArg + fourthArg
         |  }
         |
         |  def main(): Int = {
         |    ${markerStart}simpleFun(3 + 8, 5 > 9, "Hello", 77, 218)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 5
    val expectedProperArgsInText = List("3 + 8", "5 > 9", "\"Hello\"", "77", "218")

    invocationInfo.invokedElement shouldBe None
    verifyArgumentsInInvalidInvocation(invocationInfo, expectedArgCount, expectedProperArgsInText)
  }

  def testCallWithWrongArgumentType(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |  def simpleFun(firstArg: Int, secondArg: Boolean, thirdArg: String, fourthArg: Int): Int = {
         |    firstArg + fourthArg
         |  }
         |
         |  def main(): Int = {
         |    ${markerStart}simpleFun(3 + 8, 5, "Hello", 77)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 4
    val expectedProperArgsInText = List("3 + 8", "5", "\"Hello\"", "77")

    invocationInfo.invokedElement shouldBe None
    verifyArgumentsInInvalidInvocation(invocationInfo, expectedArgCount, expectedProperArgsInText)
  }

  def testCallWithNonExistentNamedArgument(): Unit = {
    val invocationInfo = generateInvocationInfoFor {
      s"""
         |class SomeClass {
         |  def simpleFun(firstArg: Int, secondArg: Boolean, thirdArg: String, fourthArg: Int): Int = {
         |    firstArg + fourthArg
         |  }
         |
         |  def main(): Int = {
         |    ${markerStart}simpleFun(3 + 8, 5, fthArg = 999, thirdArg = "Hello", fourthArg = 55)${markerEnd}
         |  }
         |}
         |""".stripMargin
    }

    val expectedArgCount = 1 + 5
    val expectedProperArgsInText = List("3 + 8", "5", "fthArg = 999",
      "thirdArg = \"Hello\"", "fourthArg = 55")

    invocationInfo.invokedElement shouldBe None
    verifyArgumentsInInvalidInvocation(invocationInfo, expectedArgCount, expectedProperArgsInText)
  }
}
