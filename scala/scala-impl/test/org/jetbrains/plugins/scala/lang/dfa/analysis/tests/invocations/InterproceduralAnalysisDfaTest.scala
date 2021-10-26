package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages.ConditionAlwaysTrue
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class InterproceduralAnalysisDfaTest extends ScalaDfaTestBase {

  def testSimpleCallsWithoutArguments(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = verySimpleMethod()
      |val y = verySimpleMethod()
      |x == 5
      |y == 5
      |""".stripMargin
  })(
    "x == 5" -> ConditionAlwaysTrue,
    "y == 5" -> ConditionAlwaysTrue
  )

  def testSimpleCallsWithArguments(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val z = simpleMethodWithArgs(15, 12) + simpleMethodWithArgs(2, 9)
      |z == 10
      |""".stripMargin
  })(
    "z == 10" -> ConditionAlwaysTrue
  )

  def testCallsWithNamedAndDefaultParameters(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val z = methodWithDefaultParam(15, 12) + methodWithDefaultParam(2, 9, 4)
      |z == 21
      |""".stripMargin
  })(
    "z == 21" -> ConditionAlwaysTrue
  )

  def testBanningRecursion(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |private def recursiveMethod1(x: Int): Int = {
      |  if (x < 10) recursiveMethod1(x + 1)
      |  else 2 * x
      |}
      |
      |private def recursiveMethod2(x: Int): Int = {
      |  val y = recursiveMethod2(x)
      |  2 * x
      |}
      |
      |recursiveMethod1(5) == 10
      |recursiveMethod2(5) == 10
      |
      |2 == 2
      |""".stripMargin
  })(
    "2 == 2" -> ConditionAlwaysTrue
  )

  def testLimitingInterproceduralAnalysisDepth(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |private def veryNested3(x: Int) = {
      |  3 * x
      |}
      |
      |private def veryNested2(x: Int) = {
      |  2 * x + veryNested3(x)
      |}
      |
      |private def veryNested1(x: Int) = {
      |  x + veryNested2(x)
      |}
      |
      |private def nested2(y: Int) = {
      |  2 * y
      |}
      |
      |private def nested1(x: Int) = {
      |  x + nested2(x)
      |}
      |
      |nested1(5) == 15
      |veryNested1(5) == 30
      |
      |2 == 2
      |""".stripMargin
  })(
    "nested1(5) == 15" -> ConditionAlwaysTrue,
    "2 == 2" -> ConditionAlwaysTrue
  )

  def testReactingToPossibleThrowsOrReturnsInExternalMethods(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |private def otherMethod(x: Int): Int = {
      |  3 $$ 4
      |  x
      |}
      |
      |private def goodMethod(x: Int): Boolean = {
      |  3 + 3
      |
      |  true
      |}
      |
      |private def badMethod(x: Int): Boolean = {
      |  x match { // some currently unsupported expression here
      |    case 3 => return false
      |    case _ =>
      |  }
      |
      |  true
      |}
      |
      |val x = otherMethod(5)
      |x == 5
      |goodMethod(5)
      |
      |if (badMethod(5)) {
      |  x == 3
      |}
      |
      |2 == 2
      |
      |""".stripMargin
  })(
    "2 == 2" -> ConditionAlwaysTrue,
    "goodMethod(5)" -> ConditionAlwaysTrue
  )
}
