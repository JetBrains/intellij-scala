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
      |23: POP
      |24: FINISH BlockExpression
      |25: RETURN
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
      |2: PUSH y
      |3: PUSH_VAL 100
      |4: BOOLEAN_OP <
      |5: IF_EQ false 8
      |6: PUSH_VAL true
      |7: GOTO 12
      |8: FINISH
      |9: PUSH y
      |10: PUSH_VAL 150
      |11: BOOLEAN_OP <=
      |12: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |13: NOT
      |14: IF_EQ false 20
      |15: FINISH ; flushing [y]
      |16: PUSH_VAL 0
      |17: PUSH_VAL 9
      |18: NUMERIC_OP -
      |19: GOTO 24
      |20: FINISH ; flushing [y]
      |21: PUSH_VAL 0
      |22: PUSH_VAL 3
      |23: NUMERIC_OP -
      |24: FINISH IfStatement
      |25: ASSIGN_TO x
      |26: PUSH_VAL 2
      |27: PUSH_VAL 3
      |28: BOOLEAN_OP >
      |29: ASSIGN_TO p1
      |30: PUSH p1
      |31: NOT
      |32: POP
      |33: FINISH BlockExpression
      |34: RETURN
      |""".stripMargin
  }
}
