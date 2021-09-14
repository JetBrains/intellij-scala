package org.jetbrains.plugins.scala.lang.dfa.cfg

class LiteralControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testIntegerLiteral(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |3
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: FINISH BlockExpression
      |2: RETURN
      |3: POP
      |4: RETURN
      |""".stripMargin
  }
}
