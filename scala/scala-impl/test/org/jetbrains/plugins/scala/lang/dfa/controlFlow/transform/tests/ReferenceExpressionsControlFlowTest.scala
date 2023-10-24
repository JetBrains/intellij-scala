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
      |19: POP
      |20: FINISH BlockExpression
      |21: RETURN
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
      |2: POP
      |3: PUSH x
      |4: PUSH_VAL TOP
      |5: FLUSH_ALL_FIELDS
      |6: PUSH_VAL TOP
      |7: FLUSH_ALL_FIELDS
      |8: CALL <unknown>
      |9: SPLICE [2] -> []
      |10: PUSH_VAL TOP
      |11: FLUSH_ALL_FIELDS
      |12: ASSIGN_TO y
      |13: POP
      |14: PUSH y
      |15: PUSH_VAL 2
      |16: NUMERIC_OP +
      |17: POP
      |18: FINISH BlockExpression
      |19: RETURN
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
      |11: POP
      |12: PUSH_VAL 3
      |13: PUSH_VAL 3
      |14: BOOLEAN_OP ==
      |15: ASSIGN_TO `another strange name!`
      |16: POP
      |17: PUSH `another strange name!`
      |18: IF_EQ false 23
      |19: FINISH
      |20: PUSH `some strange name`
      |21: POP
      |22: GOTO 26
      |23: FINISH
      |24: PUSH_VAL 3
      |25: POP
      |26: FINISH IfStatement; flushing [`some strange name`]
      |27: FINISH BlockExpression
      |28: RETURN
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
      |2: POP
      |3: PUSH_VAL TOP
      |4: CALL Student#apply
      |5: PUSH_VAL 22
      |6: PUSH grades
      |7: CALL Student#apply
      |8: ASSIGN_TO s1
      |9: POP
      |10: PUSH s1
      |11: POP
      |12: PUSH s1.age
      |13: PUSH_VAL 20
      |14: BOOLEAN_OP >=
      |15: POP
      |16: PUSH s1
      |17: POP
      |18: PUSH s1.grades
      |19: PUSH_VAL 5
      |20: ENSURE_INDEX size
      |21: PUSH s1
      |22: POP
      |23: PUSH s1.grades
      |24: PUSH_VAL 5
      |25: CALL LinearSeqOptimized#apply
      |26: POP
      |27: FINISH BlockExpression
      |28: RETURN
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
      |4: POP
      |5: PUSH p1
      |6: POP
      |7: PUSH p1.id
      |8: PUSH_VAL 20
      |9: BOOLEAN_OP <
      |10: POP
      |11: FINISH BlockExpression
      |12: RETURN
      |""".stripMargin
  }
}
