package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class LiteralAndSimpleExpressionsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testNullLiteral(): Unit = doTest(codeFromMethodBody(returnType = "Any") {
    """
      |null
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL null
      |1: FINISH BlockExpression
      |2: RETURN
      |3: POP
      |4: RETURN
      |""".stripMargin
  }

  def testIntegerLiterals(): Unit = doTest(codeFromMethodBody(returnType = "Int") {
    """
      |3
      |0
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: POP
      |2: PUSH_VAL 0
      |3: FINISH BlockExpression
      |4: RETURN
      |5: POP
      |6: RETURN
      |""".stripMargin
  }

  def testLongLiterals(): Unit = doTest(codeFromMethodBody(returnType = "Long") {
    """
      |33L
      |48294904928493L
      |55l
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 33L
      |1: POP
      |2: PUSH_VAL 48294904928493L
      |3: POP
      |4: PUSH_VAL 55L
      |5: FINISH BlockExpression
      |6: RETURN
      |7: POP
      |8: RETURN
      |""".stripMargin
  }

  def testFloatLiterals(): Unit = doTest(codeFromMethodBody(returnType = "Float") {
    """
      |3.14159f
      |1e30F
      |1.0e30f
      |.1f
      |""".stripMargin
  }) {
    """
      0: PUSH_VAL 3.14159
      |1: POP
      |2: PUSH_VAL 1.0E30
      |3: POP
      |4: PUSH_VAL 1.0E30
      |5: POP
      |6: PUSH_VAL 0.1
      |7: FINISH BlockExpression
      |8: RETURN
      |9: POP
      |10: RETURN
      |""".stripMargin
  }

  def testDoubleLiterals(): Unit = doTest(codeFromMethodBody(returnType = "Double") {
    """
      |3.14159
      |1e30
      |1.0e100
      |.1
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3.14159
      |1: POP
      |2: PUSH_VAL 1.0E30
      |3: POP
      |4: PUSH_VAL 1.0E100
      |5: POP
      |6: PUSH_VAL 0.1
      |7: FINISH BlockExpression
      |8: RETURN
      |9: POP
      |10: RETURN
      |""".stripMargin
  }

  def testBooleanLiterals(): Unit = doTest(codeFromMethodBody(returnType = "Boolean") {
    """
      |true
      |false
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL true
      |1: POP
      |2: PUSH_VAL false
      |3: FINISH BlockExpression
      |4: RETURN
      |5: POP
      |6: RETURN
      |""".stripMargin
  }

  def testCharLiterals(): Unit = doTest(codeFromMethodBody(returnType = "Char") {
    """
      |'k'
      |'⇒'
      |'\t'
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 107
      |1: POP
      |2: PUSH_VAL 8658
      |3: POP
      |4: PUSH_VAL 9
      |5: FINISH BlockExpression
      |6: RETURN
      |7: POP
      |8: RETURN
      |""".stripMargin
  }

  def testStringLiterals(): Unit = doTest(codeFromMethodBody(returnType = "String") {
    """
      |"Привет, \nScala ԹԿՃ"
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL TOP
      |1: FINISH BlockExpression
      |2: RETURN
      |3: POP
      |4: RETURN
      |""".stripMargin
  }

  def testUnitExpression(): Unit = doTest(codeFromMethodBody(returnType = "Unit") {
    """
      |()
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL TOP
      |1: FINISH BlockExpression
      |2: RETURN
      |3: POP
      |4: RETURN
      |""".stripMargin
  }

  def testParenthesisedExpression(): Unit = doTest(codeFromMethodBody(returnType = "Char") {
    """
      |(3)
      |(((((('c'))))))
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: POP
      |2: PUSH_VAL 99
      |3: FINISH BlockExpression
      |4: RETURN
      |5: POP
      |6: RETURN
      |""".stripMargin
  }
}
