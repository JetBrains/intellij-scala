package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class InvalidInvocationsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testInvalidSimpleCallsWithWrongNumberOfArguments(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = 15
      |anotherMethod(1000 * 3 - 9, x, 5 > 3)
      |anotherMethod(1000 * 3 - 9, x, 5 > 3, "This is good")
      |anotherMethod(1000 * 3 - 9, x, 5 > 3, "But this is not", 7777)
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
      |14: CALL <unknown>
      |15: POP
      |16: PUSH_VAL TOP
      |17: PUSH_VAL 1000
      |18: PUSH_VAL 3
      |19: NUMERIC_OP *
      |20: PUSH_VAL 9
      |21: NUMERIC_OP -
      |22: PUSH x
      |23: PUSH_VAL 5
      |24: PUSH_VAL 3
      |25: BOOLEAN_OP >
      |26: PUSH_VAL TOP
      |27: CALL TestClass#anotherMethod
      |28: POP
      |29: PUSH_VAL TOP
      |30: PUSH_VAL 1000
      |31: PUSH_VAL 3
      |32: NUMERIC_OP *
      |33: PUSH_VAL 9
      |34: NUMERIC_OP -
      |35: PUSH x
      |36: PUSH_VAL 5
      |37: PUSH_VAL 3
      |38: BOOLEAN_OP >
      |39: PUSH_VAL TOP
      |40: PUSH_VAL 7777
      |41: CALL <unknown>
      |42: FINISH BlockExpression
      |43: RETURN
      |44: POP
      |45: RETURN
      |""".stripMargin
  }

  def testNonExistingMethods(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = 333
      |anotherMetod(1000 * 3 - 9, x, 5 > 3, "???")
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 333
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
      |15: CALL <unknown>
      |16: FINISH BlockExpression
      |17: RETURN
      |18: POP
      |19: RETURN
      |""".stripMargin
  }

  def testNonExistingInfixOperators(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |2 + 7 $ 3 * 8 dd 9
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 2
      |1: PUSH_VAL 7
      |2: NUMERIC_OP +
      |3: PUSH_VAL 3
      |4: PUSH_VAL 8
      |5: NUMERIC_OP *
      |6: CALL <unknown>
      |7: PUSH_VAL 9
      |8: CALL <unknown>
      |9: FINISH BlockExpression
      |10: RETURN
      |11: POP
      |12: RETURN
      |""".stripMargin
  }
}
