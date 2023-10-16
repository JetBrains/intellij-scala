package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class ReferenceExpressionsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testReferencesToMethodArgs(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
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
      |2: PUSH_VAL TOP
      |3: POP
      |4: PUSH_VAL 2193
      |5: PUSH_VAL 2
      |6: NUMERIC_OP +
      |7: ASSIGN_TO y
      |8: PUSH_VAL TOP
      |9: POP
      |10: PUSH arg1
      |11: PUSH x
      |12: PUSH arg2
      |13: NUMERIC_OP *
      |14: NUMERIC_OP +
      |15: POP
      |16: PUSH x
      |17: PUSH y
      |18: NUMERIC_OP +
      |19: POP
      |20: PUSH x
      |21: FINISH BlockExpression
      |22: RETURN
      |23: POP
      |24: RETURN
      |""".stripMargin
  }

  def testUnknownReferences(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
    """
      |val x = 15
      |val y = x + `k` * anotherUnknown
      |y + 2
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 15
      |1: ASSIGN_TO x
      |2: PUSH_VAL TOP
      |3: POP
      |4: PUSH x
      |5: PUSH_VAL TOP
      |6: FLUSH_ALL_FIELDS
      |7: PUSH_VAL TOP
      |8: FLUSH_ALL_FIELDS
      |9: CALL <unknown>
      |10: SPLICE [2] -> []
      |11: PUSH_VAL TOP
      |12: FLUSH_ALL_FIELDS
      |13: ASSIGN_TO y
      |14: PUSH_VAL TOP
      |15: POP
      |16: PUSH y
      |17: PUSH_VAL 2
      |18: NUMERIC_OP +
      |19: FINISH BlockExpression
      |20: RETURN
      |21: POP
      |22: RETURN
      |""".stripMargin
  }

  def testLiteralIdentifierReferences(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
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
      |11: PUSH_VAL TOP
      |12: POP
      |13: PUSH_VAL 3
      |14: PUSH_VAL 3
      |15: BOOLEAN_OP ==
      |16: ASSIGN_TO `another strange name!`
      |17: PUSH_VAL TOP
      |18: POP
      |19: PUSH `another strange name!`
      |20: IF_EQ false 24
      |21: FINISH
      |22: PUSH `some strange name`
      |23: GOTO 26
      |24: FINISH
      |25: PUSH_VAL 3
      |26: FINISH IfStatement; flushing [`some strange name`]
      |27: FINISH BlockExpression
      |28: RETURN
      |29: POP
      |30: RETURN
      |""".stripMargin
  }

  def testCreatingAndAccessingCaseClasses(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
    """
      |val grades = Nil
      |val s1 = Student(22, grades)
      |s1.age >= 20
      |s1.grades(5)
      |""".stripMargin
  }) {
    """
      |0: PUSH Nil
      |1: ASSIGN_TO grades
      |2: PUSH_VAL TOP
      |3: POP
      |4: PUSH_VAL TOP
      |5: CALL Student#apply
      |6: PUSH_VAL 22
      |7: PUSH grades
      |8: CALL Student#apply
      |9: ASSIGN_TO s1
      |10: PUSH_VAL TOP
      |11: POP
      |12: PUSH s1
      |13: POP
      |14: PUSH s1.age
      |15: PUSH_VAL 20
      |16: BOOLEAN_OP >=
      |17: POP
      |18: PUSH s1
      |19: POP
      |20: PUSH s1.grades
      |21: PUSH_VAL 5
      |22: ENSURE_INDEX size
      |23: PUSH s1
      |24: POP
      |25: PUSH s1.grades
      |26: PUSH_VAL 5
      |27: CALL LinearSeqOptimized#apply
      |28: FINISH BlockExpression
      |29: RETURN
      |30: POP
      |31: RETURN
      |""".stripMargin
  }

  def testCreatingAndAccessingRegularClasses(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |val p1 = new Person(33)
      |p1.id < 20
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL TOP
      |1: PUSH_VAL 33
      |2: CALL Person#Person
      |3: ASSIGN_TO p1
      |4: PUSH_VAL TOP
      |5: POP
      |6: PUSH p1
      |7: POP
      |8: PUSH p1.id
      |9: PUSH_VAL 20
      |10: BOOLEAN_OP <
      |11: FINISH BlockExpression
      |12: RETURN
      |13: POP
      |14: RETURN
      |""".stripMargin
  }
}
