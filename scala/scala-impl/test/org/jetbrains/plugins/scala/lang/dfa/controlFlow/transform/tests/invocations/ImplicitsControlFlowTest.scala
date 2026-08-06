package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilderTestBase

class ImplicitsControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {
  def test_implicit_class(): Unit = doTest(codeFromMethodBody(returnType = "Unit")(
    """
      |implicit class TestClass(val x: Int) {
      |  def blub(): Unit = ()
      |}
      |
      |1.blub()
      |""".stripMargin
  )) {
    """
      |0: PUSH_VAL 1
      |1: CALL TestClass#TestClass
      |2: CALL TestClass#blub
      |3: POP
      |4: FINISH BlockExpression
      |5: RETURN
      |""".stripMargin
  }


  def test_implicit_conversion(): Unit = doTest(codeFromMethodBody(returnType = "Unit")(
    """
      |class TestClass(val x: Int) {
      |  def blub(): Unit = ()
      |}
      |
      |implicit def toTestClass(x: Int): TestClass = new TestClass(x)
      |
      |1.blub()
      |""".stripMargin
  )) {
    """
      |0: PUSH_VAL 1
      |1: CALL toTestClass
      |2: CALL TestClass#blub
      |3: POP
      |4: FINISH BlockExpression
      |5: RETURN
      |""".stripMargin
  }


}
