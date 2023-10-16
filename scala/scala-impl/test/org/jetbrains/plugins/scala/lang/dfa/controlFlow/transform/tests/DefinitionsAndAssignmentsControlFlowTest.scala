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
      |4: PUSH_VAL TOP
      |5: POP
      |6: PUSH_VAL 3
      |7: PUSH_VAL 8
      |8: NUMERIC_OP *
      |9: PUSH_VAL 15
      |10: NUMERIC_OP +
      |11: ASSIGN_TO x
      |12: PUSH_VAL TOP
      |13: POP
      |14: PUSH booleanVal
      |15: IF_EQ false 25
      |16: FINISH
      |17: PUSH_VAL 2
      |18: PUSH x
      |19: PUSH_VAL 7
      |20: NUMERIC_OP *
      |21: NUMERIC_OP -
      |22: PUSH_VAL 3
      |23: NUMERIC_OP +
      |24: GOTO 29
      |25: FINISH
      |26: PUSH_VAL 5
      |27: PUSH x
      |28: NUMERIC_OP -
      |29: FINISH IfStatement; flushing [x]
      |30: FINISH BlockExpression
      |31: RETURN
      |32: POP
      |33: RETURN
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
      |4: PUSH_VAL TOP
      |5: POP
      |6: PUSH_VAL 9
      |7: ASSIGN_TO x
      |8: PUSH_VAL TOP
      |9: POP
      |10: PUSH x
      |11: PUSH_VAL 10
      |12: BOOLEAN_OP >
      |13: POP
      |14: PUSH_VAL 8
      |15: ASSIGN_TO x
      |16: PUSH_VAL TOP
      |17: POP
      |18: PUSH x
      |19: PUSH_VAL 11
      |20: BOOLEAN_OP >
      |21: POP
      |22: PUSH_VAL 14
      |23: ASSIGN_TO x
      |24: PUSH_VAL TOP
      |25: POP
      |26: PUSH x
      |27: PUSH_VAL 12
      |28: BOOLEAN_OP >
      |29: POP
      |30: PUSH y
      |31: ASSIGN_TO x
      |32: PUSH_VAL TOP
      |33: POP
      |34: PUSH x
      |35: PUSH_VAL 10
      |36: BOOLEAN_OP ==
      |37: FINISH BlockExpression
      |38: RETURN
      |39: POP
      |40: RETURN
      |""".stripMargin
  }
}
