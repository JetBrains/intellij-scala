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
      |4: PUSH_VAL 3
      |5: PUSH_VAL 8
      |6: NUMERIC_OP *
      |7: PUSH_VAL 15
      |8: NUMERIC_OP +
      |9: ASSIGN_TO x
      |10: PUSH booleanVal
      |11: IF_EQ false 21
      |12: FINISH
      |13: PUSH_VAL 2
      |14: PUSH x
      |15: PUSH_VAL 7
      |16: NUMERIC_OP *
      |17: NUMERIC_OP -
      |18: PUSH_VAL 3
      |19: NUMERIC_OP +
      |20: GOTO 25
      |21: FINISH
      |22: PUSH_VAL 5
      |23: PUSH x
      |24: NUMERIC_OP -
      |25: FINISH IfStatement; flushing [x]
      |26: FINISH BlockExpression
      |27: RETURN
      |28: POP
      |29: RETURN
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
      |4: PUSH_VAL 9
      |5: ASSIGN_TO x
      |6: PUSH x
      |7: PUSH_VAL 10
      |8: BOOLEAN_OP >
      |9: POP
      |10: PUSH_VAL 8
      |11: ASSIGN_TO x
      |12: PUSH x
      |13: PUSH_VAL 11
      |14: BOOLEAN_OP >
      |15: POP
      |16: PUSH_VAL 14
      |17: ASSIGN_TO x
      |18: PUSH x
      |19: PUSH_VAL 12
      |20: BOOLEAN_OP >
      |21: POP
      |22: PUSH y
      |23: ASSIGN_TO x
      |24: PUSH x
      |25: PUSH_VAL 10
      |26: BOOLEAN_OP ==
      |27: FINISH BlockExpression
      |28: RETURN
      |29: POP
      |30: RETURN
      |""".stripMargin
  }
}
