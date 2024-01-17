package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class DefinitionsAndAssignmentsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testDefiningSimpleValuesAndVariables(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
    """
      |val booleanVal = 3 > 2
      |var x = 3 * 8 + 15
      |if (booleanVal) 2 - x * 7 + 3
      |else 5 - x
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP >
      |3: ASSIGN_TO booleanVal
      |4: POP
      |5: PUSH_VAL 3
      |6: PUSH_VAL 8
      |7: NUMERIC_OP *
      |8: PUSH_VAL 15
      |9: NUMERIC_OP +
      |10: ASSIGN_TO x
      |11: POP
      |12: PUSH booleanVal
      |13: IF_EQ false 24
      |14: FINISH
      |15: PUSH_VAL 2
      |16: PUSH x
      |17: PUSH_VAL 7
      |18: NUMERIC_OP *
      |19: NUMERIC_OP -
      |20: PUSH_VAL 3
      |21: NUMERIC_OP +
      |22: POP
      |23: GOTO 29
      |24: FINISH
      |25: PUSH_VAL 5
      |26: PUSH x
      |27: NUMERIC_OP -
      |28: POP
      |29: FINISH IfStatement; flushing [x]
      |30: FINISH BlockExpression
      |31: RETURN
      |""".stripMargin
  }

  def testReassigningVars(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |var y = 5 * 2
      |var x = 9
      |x > 10
      |x = 8
      |x > 11
      |x = 14
      |x > 12
      |x = y
      |x == 10
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 5
      |1: PUSH_VAL 2
      |2: NUMERIC_OP *
      |3: ASSIGN_TO y
      |4: POP
      |5: PUSH_VAL 9
      |6: ASSIGN_TO x
      |7: POP
      |8: PUSH x
      |9: PUSH_VAL 10
      |10: BOOLEAN_OP >
      |11: POP
      |12: PUSH_VAL 8
      |13: ASSIGN_TO x
      |14: POP
      |15: PUSH x
      |16: PUSH_VAL 11
      |17: BOOLEAN_OP >
      |18: POP
      |19: PUSH_VAL 14
      |20: ASSIGN_TO x
      |21: POP
      |22: PUSH x
      |23: PUSH_VAL 12
      |24: BOOLEAN_OP >
      |25: POP
      |26: PUSH y
      |27: ASSIGN_TO x
      |28: POP
      |29: PUSH x
      |30: PUSH_VAL 10
      |31: BOOLEAN_OP ==
      |32: POP
      |33: FINISH BlockExpression
      |34: RETURN
      |""".stripMargin
  }
}
