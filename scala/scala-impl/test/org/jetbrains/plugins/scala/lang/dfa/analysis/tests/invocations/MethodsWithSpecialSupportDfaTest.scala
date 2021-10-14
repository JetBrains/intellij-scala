package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class MethodsWithSpecialSupportDfaTest extends ScalaDfaTestBase {

  def testSpecialSupportForLists(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val list1 = List(3, 5, 8)
      |val list2 = 3 :: 5 :: Nil
      |val list3 = List(3, 6)
      |list1 == list2
      |list1 == list3
      |list2 == list3
      |""".stripMargin
  })(
    "list1 == list2" -> ConditionAlwaysFalse,
    "list1 == list3" -> ConditionAlwaysFalse
  )

  def testJavaMethodsWithCustomHandlers(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = sqrt(9)
      |val list = util.List.of(2, 7)
      |list.add(9) // List.of returns an unmodifiable list, so this can't affect it
      |val y = list.indexOf(3)
      |x == 3.0
      |x == 3.1
      |y == 2
      |""".stripMargin
  })(
    "x == 3.0" -> ConditionAlwaysTrue,
    "x == 3.1" -> ConditionAlwaysFalse,
    "y == 2" -> ConditionAlwaysFalse
  )
}
