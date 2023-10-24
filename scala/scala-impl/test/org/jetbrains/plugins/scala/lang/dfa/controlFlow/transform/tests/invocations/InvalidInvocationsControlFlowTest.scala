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
      |13: CALL <unknown>
      |14: POP
      |15: PUSH_VAL TOP
      |16: PUSH_VAL 1000
      |17: PUSH_VAL 3
      |18: NUMERIC_OP *
      |19: PUSH_VAL 9
      |20: NUMERIC_OP -
      |21: PUSH x
      |22: PUSH_VAL 5
      |23: PUSH_VAL 3
      |24: BOOLEAN_OP >
      |25: PUSH_VAL "This is good"
      |26: CALL TestClass#anotherMethod
      |27: POP
      |28: PUSH_VAL TOP
      |29: PUSH_VAL 1000
      |30: PUSH_VAL 3
      |31: NUMERIC_OP *
      |32: PUSH_VAL 9
      |33: NUMERIC_OP -
      |34: PUSH x
      |35: PUSH_VAL 5
      |36: PUSH_VAL 3
      |37: BOOLEAN_OP >
      |38: PUSH_VAL "But this is not"
      |39: PUSH_VAL 7777
      |40: CALL <unknown>
      |41: POP
      |42: FINISH BlockExpression
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
      |13: PUSH_VAL "???"
      |14: CALL <unknown>
      |15: POP
      |16: FINISH BlockExpression
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
      |9: POP
      |10: FINISH BlockExpression
      |11: RETURN
      |""".stripMargin
  }
}
