package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ScalaDfaControlFlowBuilderTestBase

class RegularMethodCallControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testSimpleMethodCalls(): Unit = test(codeFromMethodBody(returnType = "Int") {
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
      |13: PUSH_VAL TOP
      |14: CALL TestClass#anotherMethod
      |15: FINISH BlockExpression
      |16: RETURN
      |17: POP
      |18: RETURN
      |""".stripMargin
  }
}
