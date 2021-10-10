package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ScalaDfaControlFlowBuilderTestBase

class DefinitionsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testDefiningSimpleValuesAndVariables(): Unit = test(codeFromMethodBody(returnType = "Int") {
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
      |13: IF_EQ false 23
      |14: FINISH
      |15: PUSH_VAL 2
      |16: PUSH x
      |17: PUSH_VAL 7
      |18: NUMERIC_OP *
      |19: NUMERIC_OP -
      |20: PUSH_VAL 3
      |21: NUMERIC_OP +
      |22: GOTO 27
      |23: FINISH
      |24: PUSH_VAL 5
      |25: PUSH x
      |26: NUMERIC_OP -
      |27: FINISH IfStatement; flushing [x]
      |28: FINISH BlockExpression
      |29: RETURN
      |30: POP
      |31: RETURN
      |""".stripMargin
  }
}
