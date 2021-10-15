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
}
