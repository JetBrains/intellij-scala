package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class UnaryPrefixExpressionsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testNumericUnaryOperators(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |(-3) + 5 == 2
      |5 - -3 == 9
      |+0 == -0
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 0
      |1: PUSH_VAL 3
      |2: NUMERIC_OP -
      |3: PUSH_VAL 5
      |4: NUMERIC_OP +
      |5: PUSH_VAL 2
      |6: BOOLEAN_OP ==
      |7: POP
      |8: PUSH_VAL 5
      |9: PUSH_VAL 0
      |10: PUSH_VAL 3
      |11: NUMERIC_OP -
      |12: NUMERIC_OP -
      |13: PUSH_VAL 9
      |14: BOOLEAN_OP ==
      |15: POP
      |16: PUSH_VAL 0
      |17: PUSH_VAL 0
      |18: NUMERIC_OP +
      |19: PUSH_VAL 0
      |20: PUSH_VAL 0
      |21: NUMERIC_OP -
      |22: BOOLEAN_OP ==
      |23: FINISH BlockExpression
      |24: RETURN
      |25: POP
      |26: RETURN
      |""".stripMargin
  }

  def testLogicalUnaryOperators(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |val y = 200
      |val x = if (!(y < 100 || y <= 150)) -9 else -3
      |val p1 = 2 > 3
      |!p1
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 200
      |1: ASSIGN_TO y
      |2: PUSH_VAL TOP
      |3: POP
      |4: PUSH y
      |5: PUSH_VAL 100
      |6: BOOLEAN_OP <
      |7: IF_EQ false 10
      |8: PUSH_VAL true
      |9: GOTO 14
      |10: FINISH
      |11: PUSH y
      |12: PUSH_VAL 150
      |13: BOOLEAN_OP <=
      |14: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |15: NOT
      |16: IF_EQ false 22
      |17: FINISH ; flushing [y]
      |18: PUSH_VAL 0
      |19: PUSH_VAL 9
      |20: NUMERIC_OP -
      |21: GOTO 26
      |22: FINISH ; flushing [y]
      |23: PUSH_VAL 0
      |24: PUSH_VAL 3
      |25: NUMERIC_OP -
      |26: FINISH IfStatement
      |27: ASSIGN_TO x
      |28: PUSH_VAL TOP
      |29: POP
      |30: PUSH_VAL 2
      |31: PUSH_VAL 3
      |32: BOOLEAN_OP >
      |33: ASSIGN_TO p1
      |34: PUSH_VAL TOP
      |35: POP
      |36: PUSH p1
      |37: NOT
      |38: FINISH BlockExpression
      |39: RETURN
      |40: POP
      |41: RETURN
      |""".stripMargin
  }
}
