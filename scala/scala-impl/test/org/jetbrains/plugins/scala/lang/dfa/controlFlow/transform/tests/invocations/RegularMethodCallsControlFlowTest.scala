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
      |3: PUSH_VAL 1000
      |4: PUSH_VAL 3
      |5: NUMERIC_OP *
      |6: PUSH_VAL 9
      |7: NUMERIC_OP -
      |8: PUSH x
      |9: PUSH_VAL 5
      |10: PUSH_VAL 3
      |11: BOOLEAN_OP >
      |12: PUSH_VAL "Something"
      |13: CALL TestClass#anotherMethod
      |14: POP
      |15: FINISH BlockExpression
      |16: RETURN
      |""".stripMargin
  }
}
