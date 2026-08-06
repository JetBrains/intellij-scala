package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class LeftAssocInfixOperatorsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testArithmeticOperators(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
    """
      |((((((3214 + 85901 + 77)))))) + 5
      |((3 + 2) * 7 / 5 - 1) % 2
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3214
      |1: PUSH_VAL 85901
      |2: NUMERIC_OP +
      |3: PUSH_VAL 77
      |4: NUMERIC_OP +
      |5: PUSH_VAL 5
      |6: NUMERIC_OP +
      |7: POP
      |8: PUSH_VAL 3
      |9: PUSH_VAL 2
      |10: NUMERIC_OP +
      |11: PUSH_VAL 7
      |12: NUMERIC_OP *
      |13: PUSH_VAL 5
      |14: NUMERIC_OP /
      |15: PUSH_VAL 1
      |16: NUMERIC_OP -
      |17: PUSH_VAL 2
      |18: NUMERIC_OP %
      |19: POP
      |20: FINISH BlockExpression
      |21: RETURN
      |""".stripMargin
  }

  def testRelationalOperators(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |2 < 2
      |2 <= 2
      |3 >= 2
      |329429 >= 94934
      |424 > 94
      |44 == 55
      |44 == 44
      |44 != 44
      |44 != 55
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 2
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP <
      |3: POP
      |4: PUSH_VAL 2
      |5: PUSH_VAL 2
      |6: BOOLEAN_OP <=
      |7: POP
      |8: PUSH_VAL 3
      |9: PUSH_VAL 2
      |10: BOOLEAN_OP >=
      |11: POP
      |12: PUSH_VAL 329429
      |13: PUSH_VAL 94934
      |14: BOOLEAN_OP >=
      |15: POP
      |16: PUSH_VAL 424
      |17: PUSH_VAL 94
      |18: BOOLEAN_OP >
      |19: POP
      |20: PUSH_VAL 44
      |21: PUSH_VAL 55
      |22: BOOLEAN_OP ==
      |23: POP
      |24: PUSH_VAL 44
      |25: PUSH_VAL 44
      |26: BOOLEAN_OP ==
      |27: POP
      |28: PUSH_VAL 44
      |29: PUSH_VAL 44
      |30: BOOLEAN_OP !=
      |31: POP
      |32: PUSH_VAL 44
      |33: PUSH_VAL 55
      |34: BOOLEAN_OP !=
      |35: POP
      |36: FINISH BlockExpression
      |37: RETURN
      |""".stripMargin
  }

  def testLogicalOperators(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |2 <= 2 && 2 <= 3 && 2 == 2 && 3 == 5 - 2
      |3 == 3 && 5 > 10
      |false || 3 != 4 || 10 < 2 || 3 != 3
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 2
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP <=
      |3: IF_EQ true 6
      |4: PUSH_VAL false
      |5: GOTO 10
      |6: FINISH
      |7: PUSH_VAL 2
      |8: PUSH_VAL 3
      |9: BOOLEAN_OP <=
      |10: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |11: IF_EQ true 14
      |12: PUSH_VAL false
      |13: GOTO 18
      |14: FINISH
      |15: PUSH_VAL 2
      |16: PUSH_VAL 2
      |17: BOOLEAN_OP ==
      |18: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |19: IF_EQ true 22
      |20: PUSH_VAL false
      |21: GOTO 28
      |22: FINISH
      |23: PUSH_VAL 3
      |24: PUSH_VAL 5
      |25: PUSH_VAL 2
      |26: NUMERIC_OP -
      |27: BOOLEAN_OP ==
      |28: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |29: POP
      |30: PUSH_VAL 3
      |31: PUSH_VAL 3
      |32: BOOLEAN_OP ==
      |33: IF_EQ true 36
      |34: PUSH_VAL false
      |35: GOTO 40
      |36: FINISH
      |37: PUSH_VAL 5
      |38: PUSH_VAL 10
      |39: BOOLEAN_OP >
      |40: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |41: POP
      |42: PUSH_VAL false
      |43: IF_EQ false 46
      |44: PUSH_VAL true
      |45: GOTO 50
      |46: FINISH
      |47: PUSH_VAL 3
      |48: PUSH_VAL 4
      |49: BOOLEAN_OP !=
      |50: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |51: IF_EQ false 54
      |52: PUSH_VAL true
      |53: GOTO 58
      |54: FINISH
      |55: PUSH_VAL 10
      |56: PUSH_VAL 2
      |57: BOOLEAN_OP <
      |58: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |59: IF_EQ false 62
      |60: PUSH_VAL true
      |61: GOTO 66
      |62: FINISH
      |63: PUSH_VAL 3
      |64: PUSH_VAL 3
      |65: BOOLEAN_OP !=
      |66: RESULT_OF ScalaStatementAnchor(InfixExpression)
      |67: POP
      |68: FINISH BlockExpression
      |69: RETURN
      |""".stripMargin
  }

  def testLogicalOperatorsAsCalls(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |2.<=(2).&&(2.<=(3)).&&(2.==(2)).&&(3.==(5.-(2)))
      |3.==(3).&&(5.>(10))
      |false.||(3.!=(4)).||(10.<(2)).||(3.!=(3))
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 2
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP <=
      |3: IF_EQ true 6
      |4: PUSH_VAL false
      |5: GOTO 10
      |6: FINISH
      |7: PUSH_VAL 2
      |8: PUSH_VAL 3
      |9: BOOLEAN_OP <=
      |10: RESULT_OF ScalaStatementAnchor(MethodCall)
      |11: IF_EQ true 14
      |12: PUSH_VAL false
      |13: GOTO 18
      |14: FINISH
      |15: PUSH_VAL 2
      |16: PUSH_VAL 2
      |17: BOOLEAN_OP ==
      |18: RESULT_OF ScalaStatementAnchor(MethodCall)
      |19: IF_EQ true 22
      |20: PUSH_VAL false
      |21: GOTO 28
      |22: FINISH
      |23: PUSH_VAL 3
      |24: PUSH_VAL 5
      |25: PUSH_VAL 2
      |26: NUMERIC_OP -
      |27: BOOLEAN_OP ==
      |28: RESULT_OF ScalaStatementAnchor(MethodCall)
      |29: POP
      |30: PUSH_VAL 3
      |31: PUSH_VAL 3
      |32: BOOLEAN_OP ==
      |33: IF_EQ true 36
      |34: PUSH_VAL false
      |35: GOTO 40
      |36: FINISH
      |37: PUSH_VAL 5
      |38: PUSH_VAL 10
      |39: BOOLEAN_OP >
      |40: RESULT_OF ScalaStatementAnchor(MethodCall)
      |41: POP
      |42: PUSH_VAL false
      |43: IF_EQ false 46
      |44: PUSH_VAL true
      |45: GOTO 50
      |46: FINISH
      |47: PUSH_VAL 3
      |48: PUSH_VAL 4
      |49: BOOLEAN_OP !=
      |50: RESULT_OF ScalaStatementAnchor(MethodCall)
      |51: IF_EQ false 54
      |52: PUSH_VAL true
      |53: GOTO 58
      |54: FINISH
      |55: PUSH_VAL 10
      |56: PUSH_VAL 2
      |57: BOOLEAN_OP <
      |58: RESULT_OF ScalaStatementAnchor(MethodCall)
      |59: IF_EQ false 62
      |60: PUSH_VAL true
      |61: GOTO 66
      |62: FINISH
      |63: PUSH_VAL 3
      |64: PUSH_VAL 3
      |65: BOOLEAN_OP !=
      |66: RESULT_OF ScalaStatementAnchor(MethodCall)
      |67: POP
      |68: FINISH BlockExpression
      |69: RETURN
      |""".stripMargin
  }
}
