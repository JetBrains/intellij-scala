package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages.ConditionAlwaysTrue
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class ReferenceExpressionDfaTest extends ScalaDfaTestBase {

  def testIgnoringReferencesToMethodArgs(): Unit = test(codeFromMethodBody(returnType = "Int") {
    """
      |var x = 15
      |val z = arg1 + x * arg2
      |x == 15
      |val zz = arg333 + x
      |x == 14 + 1
      |""".stripMargin
  })(
    "x == 15" -> ConditionAlwaysTrue
  )
}
