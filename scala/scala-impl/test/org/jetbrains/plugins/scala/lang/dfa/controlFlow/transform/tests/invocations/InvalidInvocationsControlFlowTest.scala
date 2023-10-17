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
      |3: PUSH_VAL 1000
      |4: PUSH_VAL 3
      |5: NUMERIC_OP *
      |6: PUSH_VAL 9
      |7: NUMERIC_OP -
      |8: PUSH x
      |9: PUSH_VAL 5
      |10: PUSH_VAL 3
      |11: BOOLEAN_OP >
      |12: CALL <unknown>
      |13: POP
      |14: PUSH_VAL TOP
      |15: PUSH_VAL 1000
      |16: PUSH_VAL 3
      |17: NUMERIC_OP *
      |18: PUSH_VAL 9
      |19: NUMERIC_OP -
      |20: PUSH x
      |21: PUSH_VAL 5
      |22: PUSH_VAL 3
      |23: BOOLEAN_OP >
      |24: PUSH_VAL TOP
      |25: CALL TestClass#anotherMethod
      |26: POP
      |27: PUSH_VAL TOP
      |28: PUSH_VAL 1000
      |29: PUSH_VAL 3
      |30: NUMERIC_OP *
      |31: PUSH_VAL 9
      |32: NUMERIC_OP -
      |33: PUSH x
      |34: PUSH_VAL 5
      |35: PUSH_VAL 3
      |36: BOOLEAN_OP >
      |37: PUSH_VAL TOP
      |38: PUSH_VAL 7777
      |39: CALL <unknown>
      |40: FINISH BlockExpression
      |41: RETURN
      |42: POP
      |43: RETURN
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
      |3: PUSH_VAL 1000
      |4: PUSH_VAL 3
      |5: NUMERIC_OP *
      |6: PUSH_VAL 9
      |7: NUMERIC_OP -
      |8: PUSH x
      |9: PUSH_VAL 5
      |10: PUSH_VAL 3
      |11: BOOLEAN_OP >
      |12: PUSH_VAL TOP
      |13: CALL <unknown>
      |14: FINISH BlockExpression
      |15: RETURN
      |16: POP
      |17: RETURN
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
