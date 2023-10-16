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
      |11: IF_EQ false 20
      |12: FINISH
      |13: PUSH_VAL 5
      |14: PUSH_VAL 2
      |15: NUMERIC_OP +
      |16: PUSH_VAL 20
      |17: NUMERIC_OP -
      |18: FINISH BlockExpression
      |19: GOTO 55
      |20: FINISH
      |21: PUSH_VAL 12
      |22: PUSH_VAL 13
      |23: BOOLEAN_OP ==
      |24: IF_EQ false 27
      |25: PUSH_VAL true
      |26: GOTO 31
      |27: FINISH
      |28: PUSH_VAL 13
      |29: PUSH_VAL 5
      |30: BOOLEAN_OP !=
      |31: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |32: IF_EQ false 35
      |33: PUSH_VAL true
      |34: GOTO 37
      |35: FINISH
      |36: PUSH_VAL false
      |37: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |38: IF_EQ false 49
      |39: FINISH
      |40: PUSH_VAL 7
      |41: PUSH_VAL 3
      |42: PUSH_VAL 6
      |43: NUMERIC_OP *
      |44: PUSH_VAL 2
      |45: NUMERIC_OP %
      |46: NUMERIC_OP +
      |47: FINISH BlockExpression
      |48: GOTO 54
      |49: FINISH
      |50: PUSH_VAL 9
      |51: PUSH_VAL 3
      |52: NUMERIC_OP *
      |53: FINISH BlockExpression
      |54: FINISH IfStatement
      |55: FINISH IfStatement
      |56: FINISH BlockExpression
      |57: RETURN
      |58: POP
      |59: RETURN
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
      |11: IF_EQ false 20
      |12: FINISH
      |13: PUSH_VAL 5
      |14: PUSH_VAL 2
      |15: NUMERIC_OP +
      |16: PUSH_VAL 20
      |17: NUMERIC_OP -
      |18: FINISH BlockExpression
      |19: GOTO 22
      |20: FINISH
      |21: PUSH_VAL TOP
      |22: FINISH IfStatement
      |23: FINISH BlockExpression
      |24: RETURN
      |25: POP
      |26: RETURN
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
      |11: IF_EQ false 19
      |12: FINISH
      |13: PUSH_VAL 5
      |14: PUSH_VAL 2
      |15: NUMERIC_OP +
      |16: PUSH_VAL 20
      |17: NUMERIC_OP -
      |18: GOTO 21
      |19: FINISH
      |20: PUSH_VAL TOP
      |21: FINISH IfStatement
      |22: FINISH BlockExpression
      |23: RETURN
      |24: POP
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
      |11: IF_EQ false 15
      |12: FINISH
      |13: PUSH_VAL TOP
      |14: GOTO 17
      |15: FINISH
      |16: PUSH_VAL TOP
      |17: FINISH IfStatement
      |18: FINISH BlockExpression
      |19: RETURN
      |20: POP
      |21: RETURN
      |""".stripMargin
  }
}
