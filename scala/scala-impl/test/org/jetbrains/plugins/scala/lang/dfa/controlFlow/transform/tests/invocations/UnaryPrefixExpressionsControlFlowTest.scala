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
      |2: POP
      |3: PUSH y
      |4: PUSH_VAL 100
      |5: BOOLEAN_OP <
      |6: IF_EQ false 9
      |7: PUSH_VAL true
      |8: GOTO 13
      |9: FINISH
      |10: PUSH y
      |11: PUSH_VAL 150
      |12: BOOLEAN_OP <=
      |13: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |14: NOT
      |15: IF_EQ false 21
      |16: FINISH ; flushing [y]
      |17: PUSH_VAL 0
      |18: PUSH_VAL 9
      |19: NUMERIC_OP -
      |20: GOTO 25
      |21: FINISH ; flushing [y]
      |22: PUSH_VAL 0
      |23: PUSH_VAL 3
      |24: NUMERIC_OP -
      |25: FINISH IfStatement
      |26: ASSIGN_TO x
      |27: POP
      |28: PUSH_VAL 2
      |29: PUSH_VAL 3
      |30: BOOLEAN_OP >
      |31: ASSIGN_TO p1
      |32: POP
      |33: PUSH p1
      |34: NOT
      |35: POP
      |36: FINISH BlockExpression
      |37: RETURN
      |""".stripMargin
  }

  def testUnaryFloatingPoint(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |-1.0
      |-1.0F
      |""".stripMargin
  }) {
    """0: PUSH_VAL 0.0
      |1: PUSH_VAL 1.0
      |2: NUMERIC_OP -
      |3: POP
      |4: PUSH_VAL 0.0
      |5: PUSH_VAL 1.0
      |6: NUMERIC_OP -
      |7: POP
      |8: FINISH BlockExpression
      |9: RETURN
      |""".stripMargin
  }

  def testNullEquality(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |val x: AnyRef = null
      |x == null
      |null == x
      |
      |x eq null
      |null eq x
      |
      |x != null
      |null != x
      |
      |x ne null
      |null ne x
      |""".stripMargin
  })(
    """
      |0: PUSH_VAL null
      |1: ASSIGN_TO x
      |2: POP
      |3: PUSH x
      |4: PUSH_VAL null
      |5: BOOLEAN_OP ==
      |6: POP
      |7: PUSH_VAL null
      |8: PUSH x
      |9: BOOLEAN_OP ==
      |10: POP
      |11: PUSH x
      |12: PUSH_VAL null
      |13: BOOLEAN_OP ==
      |14: POP
      |15: PUSH_VAL null
      |16: PUSH x
      |17: BOOLEAN_OP ==
      |18: POP
      |19: PUSH x
      |20: PUSH_VAL null
      |21: BOOLEAN_OP !=
      |22: POP
      |23: PUSH_VAL null
      |24: PUSH x
      |25: BOOLEAN_OP !=
      |26: POP
      |27: PUSH x
      |28: PUSH_VAL null
      |29: BOOLEAN_OP !=
      |30: POP
      |31: PUSH_VAL null
      |32: PUSH x
      |33: BOOLEAN_OP !=
      |34: POP
      |35: FINISH BlockExpression
      |36: RETURN
      |""".stripMargin)
}
