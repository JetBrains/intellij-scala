package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class PatternMatchingCfgTest extends ScalaDfaControlFlowBuilderTestBase {
  def testEmptyCaseClauses(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |1 match {
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 1
      |1: POP
      |2: FINISH BlockExpression
      |3: RETURN
      |""".stripMargin
  }

  def testOneCaseClauses(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |1 match {
      |  case 2 => println()
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 1
      |1: DUP
      |2: PUSH_VAL 2
      |3: BOOLEAN_OP ==
      |4: IF_EQ false 11
      |5: POP
      |6: PUSH_VAL TOP
      |7: CALL Predef#println
      |8: POP
      |9: FINISH BlockOfExpressions
      |10: GOTO 13
      |11: POP
      |12: THROW scala.MatchError
      |13: FINISH BlockExpression
      |14: RETURN
      |""".stripMargin
  }

  def testTwoCaseClauses(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |1 match {
      |  case 2 => "a"
      |  case 3 => "b"
      |}
      |""".stripMargin
  }) {
    """0: PUSH_VAL 1
      |1: DUP
      |2: PUSH_VAL 2
      |3: BOOLEAN_OP ==
      |4: IF_EQ false 10
      |5: POP
      |6: PUSH_VAL "a"
      |7: POP
      |8: FINISH BlockOfExpressions
      |9: GOTO 21
      |10: DUP
      |11: PUSH_VAL 3
      |12: BOOLEAN_OP ==
      |13: IF_EQ false 19
      |14: POP
      |15: PUSH_VAL "b"
      |16: POP
      |17: FINISH BlockOfExpressions
      |18: GOTO 21
      |19: POP
      |20: THROW scala.MatchError
      |21: FINISH BlockExpression
      |22: RETURN
      |""".stripMargin
  }

  def testWildcard(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |1 match {
      |  case 1 => "a"
      |  case _ => "b"
      |  case 3 => "c"
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 1
      |1: DUP
      |2: PUSH_VAL 1
      |3: BOOLEAN_OP ==
      |4: IF_EQ false 10
      |5: POP
      |6: PUSH_VAL "a"
      |7: POP
      |8: FINISH BlockOfExpressions
      |9: GOTO 14
      |10: POP
      |11: PUSH_VAL "b"
      |12: POP
      |13: FINISH BlockOfExpressions
      |14: FINISH BlockExpression
      |15: RETURN
      |""".stripMargin
  }

  def testGuard(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |1 match {
      |  case 1 if false => "a"
      |  case _ if true => "b"
      |  case 3 => "c"
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 1
      |1: DUP
      |2: PUSH_VAL 1
      |3: BOOLEAN_OP ==
      |4: IF_EQ false 12
      |5: PUSH_VAL false
      |6: IF_EQ false 12
      |7: POP
      |8: PUSH_VAL "a"
      |9: POP
      |10: FINISH BlockOfExpressions
      |11: GOTO 30
      |12: PUSH_VAL true
      |13: IF_EQ false 19
      |14: POP
      |15: PUSH_VAL "b"
      |16: POP
      |17: FINISH BlockOfExpressions
      |18: GOTO 30
      |19: DUP
      |20: PUSH_VAL 3
      |21: BOOLEAN_OP ==
      |22: IF_EQ false 28
      |23: POP
      |24: PUSH_VAL "c"
      |25: POP
      |26: FINISH BlockOfExpressions
      |27: GOTO 30
      |28: POP
      |29: THROW scala.MatchError
      |30: FINISH BlockExpression
      |31: RETURN
      |""".stripMargin
  }

  def test_result(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |val s = 1 match {
      |  case 1 if false => "a"
      |  case _ if true => "b"
      |  case 3 => "c"
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 1
      |1: DUP
      |2: PUSH_VAL 1
      |3: BOOLEAN_OP ==
      |4: IF_EQ false 11
      |5: PUSH_VAL false
      |6: IF_EQ false 11
      |7: POP
      |8: PUSH_VAL "a"
      |9: FINISH BlockOfExpressions
      |10: GOTO 28
      |11: PUSH_VAL true
      |12: IF_EQ false 17
      |13: POP
      |14: PUSH_VAL "b"
      |15: FINISH BlockOfExpressions
      |16: GOTO 28
      |17: DUP
      |18: PUSH_VAL 3
      |19: BOOLEAN_OP ==
      |20: IF_EQ false 25
      |21: POP
      |22: PUSH_VAL "c"
      |23: FINISH BlockOfExpressions
      |24: GOTO 28
      |25: POP
      |26: THROW scala.MatchError
      |27: PUSH_VAL TOP
      |28: ASSIGN_TO s
      |29: FINISH BlockExpression
      |30: RETURN
      |""".stripMargin
  }
}
