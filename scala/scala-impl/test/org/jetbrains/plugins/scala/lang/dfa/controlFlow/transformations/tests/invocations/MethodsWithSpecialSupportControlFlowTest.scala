package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations.ScalaDfaControlFlowBuilderTestBase

class MethodsWithSpecialSupportControlFlowTest extends ScalaDfaControlFlowBuilderTestBase {

  def testJavaMethodsWithCustomHandlers(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = -3
      |val list = util.List.of(abs(x))
      |list.indexOf(3)
      |list.indexOf(-3)
      |""".stripMargin
  }) {
    """
      |0: PUSH_VAL 3
      |1: CALL Synthetic method: unary_-
      |2: ASSIGN_TO x
      |3: POP
      |4: PUSH List
      |5: PUSH_VAL TOP
      |6: PUSH x
      |7: CALL Math#abs
      |8: CALL List#of
      |9: ASSIGN_TO list
      |10: POP
      |11: PUSH list
      |12: PUSH_VAL 3
      |13: CALL List#indexOf
      |14: POP
      |15: PUSH list
      |16: PUSH_VAL 3
      |17: CALL Synthetic method: unary_-
      |18: CALL List#indexOf
      |19: FINISH BlockExpression
      |20: RETURN
      |21: POP
      |22: RETURN
      |""".stripMargin
  }
}
