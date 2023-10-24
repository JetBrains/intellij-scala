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
      |2: POP
      |3: PUSH_VAL TOP
      |4: PUSH_VAL 1000
      |5: PUSH_VAL 3
      |6: NUMERIC_OP *
      |7: PUSH_VAL 9
      |8: NUMERIC_OP -
      |9: PUSH x
      |10: PUSH_VAL 5
      |11: PUSH_VAL 3
      |12: BOOLEAN_OP >
      |13: PUSH_VAL "Something"
      |14: CALL TestClass#anotherMethod
      |15: POP
      |16: FINISH BlockExpression
      |17: RETURN
      |""".stripMargin
  }
}
