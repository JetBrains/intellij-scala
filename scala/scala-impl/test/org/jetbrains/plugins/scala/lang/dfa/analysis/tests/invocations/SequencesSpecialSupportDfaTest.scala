package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaCollectionAccessProblem.{indexOutOfBoundsProblem, noSuchElementProblem}

class SequencesSpecialSupportDfaTest extends ScalaDfaTestBase {

  def testApplyFactoryForLists(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val list1 = List(3, 5, 8)
      |val list2 = 3 :: 5 :: Nil
      |val list3 = List(3, 6)
      |val list4 = 5 :: 4 :: list3
      |list1 == list2
      |list1 == list3
      |list2 == list3
      |list4 == List(5, 9, 3, 2)
      |list4 == List(5, 9, 3)
      |""".stripMargin
  })(
    "list1 == list2" -> ConditionAlwaysFalse,
    "list1 == list3" -> ConditionAlwaysFalse,
    "list4 == List(5, 9, 3)" -> ConditionAlwaysFalse
  )

  def testApplyAccessOnLists(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 488
      |val y = 2
      |val list = List(4, 6, 20, x, 55)
      |
      |list(0)
      |if (arg1 == 1) {
      |  list(8)
      |} else if (arg1 == 2) {
      |  list(2 * y) + list(3 * y) + list(4 * y)
      |  // list(4 * y) not reported because exception already thrown
      |} else if (arg1 == 3) {
      |  list(3 + 1)
      |} else {
      |  list(x)
      |}
      |""".stripMargin
  })(
    "list(8)" -> indexOutOfBoundsProblem.alwaysMessage,
    "list(3 * y)" -> indexOutOfBoundsProblem.alwaysMessage,
    "list(x)" -> indexOutOfBoundsProblem.alwaysMessage
  )

  def testNotFlushingImmutableLists(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val list1 = 15 :: Nil
      |val list2 = List(4, 8, 10)
      |
      |if (arg1 == 0) {
      |  list2(4)
      |} else {
      |  list1(4)
      |}
      |""".stripMargin
  })(
    "list2(4)" -> indexOutOfBoundsProblem.alwaysMessage,
    "list1(4)" -> indexOutOfBoundsProblem.alwaysMessage
  )

  def testHeadOnLists(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val list = List()
      |list.head
      |val list2 = 15 :: list
      |list2.head
      |""".stripMargin
  })(
    "list.head" -> noSuchElementProblem.alwaysMessage
  )

  def testNilReference(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 488
      |val y = 3
      |val list1 = List(4, 6, 20, x, 55)
      |val list3 = if (x < 500) List() else List(3, 4, 9)
      |val list2 = if (x < 500) Nil else List(3, 4, 9)
      |
      |list1(2)
      |if (arg1 == 0) {
      |  list1(2 * y)
      |} else if (arg1 == 2) {
      |  list2.head
      |} else if (arg1 == 3) {
      |  list2(1)
      |} else {
      |  list3.head
      |}
      |""".stripMargin
  })(
    "list1(2 * y)" -> indexOutOfBoundsProblem.alwaysMessage,
    "list2.head" -> noSuchElementProblem.alwaysMessage,
    "list2(1)" -> indexOutOfBoundsProblem.alwaysMessage,
    "list3.head" -> noSuchElementProblem.alwaysMessage,
    "x < 500" -> ConditionAlwaysTrue,
    "x < 500" -> ConditionAlwaysTrue
  )

  def testMapMethod(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val list1 = List(4, 6, 20, 55)
      |val list2 = 30 :: list1
      |val list3 = list1.map(_ * 2)
      |list1.map(_ * 2).map(y => y - 3) == list2
      |list3(1)
      |list3(5)
      |""".stripMargin
  })(
    "list3(5)" -> indexOutOfBoundsProblem.alwaysMessage,
    "list1.map(_ * 2).map(y => y - 3) == list2" -> ConditionAlwaysFalse
  )

  def testFilterMethod(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val list1 = List(4, 6, 20, 55)
      |val list2 = 30 :: list1
      |val list3 = list1.filter(_ > 3)
      |list3 == list1
      |list1.filter(x => x == 4) == list2
      |list3(1)
      |list3(5)
      |""".stripMargin
  })(
    "list3(5)" -> indexOutOfBoundsProblem.alwaysMessage,
    "list1.filter(x => x == 4) == list2" -> ConditionAlwaysFalse
  )

  def testSizeMethod(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val list1 = List(4, 6, 20, 55)
      |val list2 = 30 :: list1
      |val list3 = list1.filter(_ > 3)
      |val x = list1.size
      |x == 3
      |list3.size > 2
      |list3.size < 6
      |list2.size != 5
      |list3 == list1
      |list1.filter(x => x == 4).size < 6
      |""".stripMargin
  })(
    "x == 3" -> ConditionAlwaysFalse,
    "list3.size < 6" -> ConditionAlwaysTrue,
    "list2.size != 5" -> ConditionAlwaysFalse,
    "list1.filter(x => x == 4).size < 6" -> ConditionAlwaysTrue,
  )
}
