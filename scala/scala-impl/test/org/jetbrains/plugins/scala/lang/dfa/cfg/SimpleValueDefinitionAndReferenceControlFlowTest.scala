package org.jetbrains.plugins.scala.lang.dfa.cfg

class SimpleValueDefinitionAndReferenceControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testSimpleValueDefinitionsAndReferences(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val booleanVal = 3 > 2
      |val x = 3 * 8 + 15
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

  def testReferencesToMethodArgs(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 15
      |val y = 2193 + 2
      |arg1 + x * arg2
      |x + y
      |x
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 15
      |1: ASSIGN_TO x
      |2: POP
      |3: PUSH_VAL 2193
      |4: PUSH_VAL 2
      |5: NUMERIC_OP +
      |6: ASSIGN_TO y
      |7: POP
      |8: PUSH arg1
      |9: PUSH x
      |10: PUSH arg2
      |11: NUMERIC_OP *
      |12: NUMERIC_OP +
      |13: POP
      |14: PUSH x
      |15: PUSH y
      |16: NUMERIC_OP +
      |17: POP
      |18: PUSH x
      |19: FINISH BlockExpression
      |20: RETURN
      |21: POP
      |22: RETURN
      |""".stripMargin
  }

  def testReferencesToUnknownValues(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 15
      |val y = x + `k` * anotherUnknown
      |y + 2
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 15
      |1: ASSIGN_TO x
      |2: POP
      |3: PUSH x
      |4: PUSH_VAL TOP
      |5: FLUSH_ALL_FIELDS
      |6: NUMERIC_OP +
      |7: ASSIGN_TO y
      |8: POP
      |9: PUSH y
      |10: PUSH_VAL 2
      |11: NUMERIC_OP +
      |12: FINISH BlockExpression
      |13: RETURN
      |14: POP
      |15: RETURN
      |""".stripMargin
  }

  def testLiteralIdentifierReferences(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |val `some strange name` = if (3 > 2) 5 else 9
      |val `another strange name!` = 3 == 3
      |if (`another strange name!`) `some strange name`
      |else 3
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP >
      |3: IF_EQ false 7
      |4: FINISH
      |5: PUSH_VAL 5
      |6: GOTO 9
      |7: FINISH
      |8: PUSH_VAL 9
      |9: FINISH IfStatement
      |10: ASSIGN_TO `some strange name`
      |11: POP
      |12: PUSH_VAL 3
      |13: PUSH_VAL 3
      |14: BOOLEAN_OP ==
      |15: ASSIGN_TO `another strange name!`
      |16: POP
      |17: PUSH `another strange name!`
      |18: IF_EQ false 22
      |19: FINISH
      |20: PUSH `some strange name`
      |21: GOTO 24
      |22: FINISH
      |23: PUSH_VAL 3
      |24: FINISH IfStatement; flushing [`some strange name`]
      |25: FINISH BlockExpression
      |26: RETURN
      |27: POP
      |28: RETURN
      |""".stripMargin
  }
}
