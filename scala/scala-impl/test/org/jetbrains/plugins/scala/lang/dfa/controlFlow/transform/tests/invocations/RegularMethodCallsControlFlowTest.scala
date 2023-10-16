package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class RegularMethodCallsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testSimpleMethodCalls(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 15
      |anotherMethod(1000 * 3 - 9, x, 5 > 3, "Something")
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 15
      |1: ASSIGN_TO x
      |2: PUSH_VAL TOP
      |3: POP
      |4: PUSH_VAL TOP
      |5: PUSH_VAL 1000
      |6: PUSH_VAL 3
      |7: NUMERIC_OP *
      |8: PUSH_VAL 9
      |9: NUMERIC_OP -
      |10: PUSH x
      |11: PUSH_VAL 5
      |12: PUSH_VAL 3
      |13: BOOLEAN_OP >
      |14: PUSH_VAL TOP
      |15: CALL TestClass#anotherMethod
      |16: FINISH BlockExpression
      |17: RETURN
      |18: POP
      |19: RETURN
      |""".stripMargin
  }
}
