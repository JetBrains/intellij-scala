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
      |2: PUSH_VAL 2193
      |3: PUSH_VAL 2
      |4: NUMERIC_OP +
      |5: ASSIGN_TO y
      |6: PUSH arg1
      |7: PUSH x
      |8: PUSH arg2
      |9: NUMERIC_OP *
      |10: NUMERIC_OP +
      |11: POP
      |12: PUSH x
      |13: PUSH y
      |14: NUMERIC_OP +
      |15: POP
      |16: PUSH x
      |17: FINISH BlockExpression
      |18: RETURN
      |19: POP
      |20: RETURN
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
      |2: PUSH x
      |3: PUSH_VAL TOP
      |4: FLUSH_ALL_FIELDS
      |5: PUSH_VAL TOP
      |6: FLUSH_ALL_FIELDS
      |7: CALL <unknown>
      |8: SPLICE [2] -> []
      |9: PUSH_VAL TOP
      |10: FLUSH_ALL_FIELDS
      |11: ASSIGN_TO y
      |12: PUSH y
      |13: PUSH_VAL 2
      |14: NUMERIC_OP +
      |15: FINISH BlockExpression
      |16: RETURN
      |17: POP
      |18: RETURN
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
      |11: PUSH_VAL 3
      |12: PUSH_VAL 3
      |13: BOOLEAN_OP ==
      |14: ASSIGN_TO `another strange name!`
      |15: PUSH `another strange name!`
      |16: IF_EQ false 20
      |17: FINISH
      |18: PUSH `some strange name`
      |19: GOTO 22
      |20: FINISH
      |21: PUSH_VAL 3
      |22: FINISH IfStatement; flushing [`some strange name`]
      |23: FINISH BlockExpression
      |24: RETURN
      |25: POP
      |26: RETURN
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
      |3: CALL Student#apply
      |4: PUSH_VAL 22
      |5: PUSH grades
      |6: CALL Student#apply
      |7: ASSIGN_TO s1
      |8: PUSH s1
      |9: POP
      |10: PUSH s1.age
      |11: PUSH_VAL 20
      |12: BOOLEAN_OP >=
      |13: POP
      |14: PUSH s1
      |15: POP
      |16: PUSH s1.grades
      |17: PUSH_VAL 5
      |18: ENSURE_INDEX size
      |19: PUSH s1
      |20: POP
      |21: PUSH s1.grades
      |22: PUSH_VAL 5
      |23: CALL LinearSeqOptimized#apply
      |24: FINISH BlockExpression
      |25: RETURN
      |26: POP
      |27: RETURN
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
      |4: PUSH p1
      |5: POP
      |6: PUSH p1.id
      |7: PUSH_VAL 20
      |8: BOOLEAN_OP <
      |9: FINISH BlockExpression
      |10: RETURN
      |11: POP
      |12: RETURN
      |""".stripMargin
  }
}
