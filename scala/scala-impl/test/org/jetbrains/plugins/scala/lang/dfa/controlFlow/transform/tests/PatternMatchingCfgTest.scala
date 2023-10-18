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
      |2: THROW scala.MatchError
      |3: FINISH BlockExpression
      |4: RETURN
      |5: POP
      |6: RETURN
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
      |1: PUSH_VAL 2
      |2: BOOLEAN_OP ==
      |3: IF_EQ false 8
      |4: PUSH_VAL TOP
      |5: CALL Predef#println
      |6: FINISH BlockOfExpressions
      |7: GOTO 9
      |8: THROW scala.MatchError
      |9: FINISH BlockExpression
      |10: RETURN
      |11: POP
      |12: RETURN
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
      |4: IF_EQ false 8
      |5: PUSH_VAL "a"
      |6: FINISH BlockOfExpressions
      |7: GOTO 15
      |8: PUSH_VAL 3
      |9: BOOLEAN_OP ==
      |10: IF_EQ false 14
      |11: PUSH_VAL "b"
      |12: FINISH BlockOfExpressions
      |13: GOTO 15
      |14: THROW scala.MatchError
      |15: FINISH BlockExpression
      |16: RETURN
      |17: POP
      |18: RETURN
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
      |4: IF_EQ false 8
      |5: PUSH_VAL "a"
      |6: FINISH BlockOfExpressions
      |7: GOTO 11
      |8: POP
      |9: PUSH_VAL "b"
      |10: FINISH BlockOfExpressions
      |11: FINISH BlockExpression
      |12: RETURN
      |13: POP
      |14: RETURN
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
      |4: IF_EQ false 10
      |5: PUSH_VAL false
      |6: IF_EQ false 10
      |7: PUSH_VAL "a"
      |8: FINISH BlockOfExpressions
      |9: GOTO 23
      |10: POP
      |11: PUSH_VAL true
      |12: IF_EQ false 16
      |13: PUSH_VAL "b"
      |14: FINISH BlockOfExpressions
      |15: GOTO 23
      |16: PUSH_VAL 3
      |17: BOOLEAN_OP ==
      |18: IF_EQ false 22
      |19: PUSH_VAL "c"
      |20: FINISH BlockOfExpressions
      |21: GOTO 23
      |22: THROW scala.MatchError
      |23: FINISH BlockExpression
      |24: RETURN
      |25: POP
      |26: RETURN
      |""".stripMargin
  }
}
