package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class CollectionAccessDfaTest extends ScalaDfaTestBase {

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
    "list(8)" -> InvocationIndexOutOfBounds,
    "list(3 * y)" -> InvocationIndexOutOfBounds,
    "list(x)" -> InvocationIndexOutOfBounds
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
    "list2(4)" -> InvocationIndexOutOfBounds,
    "list1(4)" -> InvocationIndexOutOfBounds
  )

  def testHeadOnLists(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val list = List()
      |list.head
      |val list2 = 15 :: list
      |list2.head
      |""".stripMargin
  })(
    "list.head" -> InvocationNoSuchElement
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
    "list1(2 * y)" -> InvocationIndexOutOfBounds,
    "list2.head" -> InvocationNoSuchElement,
    "list2(1)" -> InvocationIndexOutOfBounds,
    "list3.head" -> InvocationNoSuchElement,
    "x < 500" -> ConditionAlwaysTrue,
    "x < 500" -> ConditionAlwaysTrue
  )
}
