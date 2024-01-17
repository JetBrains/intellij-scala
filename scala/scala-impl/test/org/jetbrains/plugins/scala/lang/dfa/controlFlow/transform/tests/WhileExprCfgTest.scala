package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class WhileExprCfgTest extends ScalaDfaControlFlowBuilderTestBase {
  def testRegularDoWhile(): Unit = doTest(codeFromMethodBody(returnType = "Unit"){
    """
      |do {
      |  val x = 0
      |} while (true)
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 0
      |1: ASSIGN_TO x
      |2: POP
      |3: FINISH BlockExpression
      |4: PUSH_VAL true
      |5: IF_EQ true 0
      |6: FINISH BlockExpression
      |7: RETURN
      |""".stripMargin
  }

  def testRegularWhile(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |while (true) {
      |  val x = 0
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL true
      |1: IF_EQ false 7
      |2: PUSH_VAL 0
      |3: ASSIGN_TO x
      |4: POP
      |5: FINISH BlockExpression
      |6: GOTO 0
      |7: FINISH BlockExpression
      |8: RETURN
      |""".stripMargin
  }

  def testNestedWhile(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |while (true) {
      |  do {
      |    print()
      |  } while (false)
      |}
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL true
      |1: IF_EQ false 11
      |2: PUSH_VAL TOP
      |3: PUSH_VAL ()
      |4: CALL Predef#print
      |5: POP
      |6: FINISH BlockExpression
      |7: PUSH_VAL false
      |8: IF_EQ true 2
      |9: FINISH BlockExpression
      |10: GOTO 0
      |11: FINISH BlockExpression
      |12: RETURN
      |""".stripMargin
  }
}
