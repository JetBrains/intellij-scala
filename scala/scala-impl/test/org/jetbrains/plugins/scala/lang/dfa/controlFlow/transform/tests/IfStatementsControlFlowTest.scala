package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class IfStatementsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testRegularIfs(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
    """
      |if (3 < 2 && 5 <= 7) {
      |  5 + 2 - 20
      |} else if (12 == 13 || 13 != 5 || false) {
      |  7 + 3 * 6 % 2
      |} else {
      |  9 * 3
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP <
      |3: IF_EQ true 6
      |4: PUSH_VAL false
      |5: GOTO 10
      |6: FINISH
      |7: PUSH_VAL 5
      |8: PUSH_VAL 7
      |9: BOOLEAN_OP <=
      |10: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |11: IF_EQ false 21
      |12: FINISH
      |13: PUSH_VAL 5
      |14: PUSH_VAL 2
      |15: NUMERIC_OP +
      |16: PUSH_VAL 20
      |17: NUMERIC_OP -
      |18: POP
      |19: FINISH BlockExpression
      |20: GOTO 58
      |21: FINISH
      |22: PUSH_VAL 12
      |23: PUSH_VAL 13
      |24: BOOLEAN_OP ==
      |25: IF_EQ false 28
      |26: PUSH_VAL true
      |27: GOTO 32
      |28: FINISH
      |29: PUSH_VAL 13
      |30: PUSH_VAL 5
      |31: BOOLEAN_OP !=
      |32: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |33: IF_EQ false 36
      |34: PUSH_VAL true
      |35: GOTO 38
      |36: FINISH
      |37: PUSH_VAL false
      |38: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |39: IF_EQ false 51
      |40: FINISH
      |41: PUSH_VAL 7
      |42: PUSH_VAL 3
      |43: PUSH_VAL 6
      |44: NUMERIC_OP *
      |45: PUSH_VAL 2
      |46: NUMERIC_OP %
      |47: NUMERIC_OP +
      |48: POP
      |49: FINISH BlockExpression
      |50: GOTO 57
      |51: FINISH
      |52: PUSH_VAL 9
      |53: PUSH_VAL 3
      |54: NUMERIC_OP *
      |55: POP
      |56: FINISH BlockExpression
      |57: FINISH IfStatement
      |58: FINISH IfStatement
      |59: FINISH BlockExpression
      |60: RETURN
      |""".stripMargin
  }

  def testIfsWithoutElseBranch(): Unit = doTest(codeFromMethodBody(returnType = "Any") {
    """
      |if (3 < 2 && 5 <= 7) {
      |  5 + 2 - 20
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP <
      |3: IF_EQ true 6
      |4: PUSH_VAL false
      |5: GOTO 10
      |6: FINISH
      |7: PUSH_VAL 5
      |8: PUSH_VAL 7
      |9: BOOLEAN_OP <=
      |10: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |11: IF_EQ false 21
      |12: FINISH
      |13: PUSH_VAL 5
      |14: PUSH_VAL 2
      |15: NUMERIC_OP +
      |16: PUSH_VAL 20
      |17: NUMERIC_OP -
      |18: POP
      |19: FINISH BlockExpression
      |20: GOTO 22
      |21: FINISH
      |22: FINISH IfStatement
      |23: FINISH BlockExpression
      |24: RETURN
      |""".stripMargin
  }

  def testIfWithExplicitUnitElseBranch(): Unit = doTest(codeFromMethodBody(returnType = "Any") {
    """
      |if (3 < 2 && 5 <= 7) 5 + 2 - 20
      |else ()
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP <
      |3: IF_EQ true 6
      |4: PUSH_VAL false
      |5: GOTO 10
      |6: FINISH
      |7: PUSH_VAL 5
      |8: PUSH_VAL 7
      |9: BOOLEAN_OP <=
      |10: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |11: IF_EQ false 20
      |12: FINISH
      |13: PUSH_VAL 5
      |14: PUSH_VAL 2
      |15: NUMERIC_OP +
      |16: PUSH_VAL 20
      |17: NUMERIC_OP -
      |18: POP
      |19: GOTO 23
      |20: FINISH
      |21: PUSH_VAL ()
      |22: POP
      |23: FINISH IfStatement
      |24: FINISH BlockExpression
      |25: RETURN
      |""".stripMargin
  }

  def testIfsWithBothBranchesEmpty(): Unit = doTest(codeFromMethodBody(returnType = "Any") {
    """
      |if (3 < 2 && 5 <= 7) {
      |} else {
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP <
      |3: IF_EQ true 6
      |4: PUSH_VAL false
      |5: GOTO 10
      |6: FINISH
      |7: PUSH_VAL 5
      |8: PUSH_VAL 7
      |9: BOOLEAN_OP <=
      |10: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |11: IF_EQ false 14
      |12: FINISH
      |13: GOTO 15
      |14: FINISH
      |15: FINISH IfStatement
      |16: FINISH BlockExpression
      |17: RETURN
      |""".stripMargin
  }
}
