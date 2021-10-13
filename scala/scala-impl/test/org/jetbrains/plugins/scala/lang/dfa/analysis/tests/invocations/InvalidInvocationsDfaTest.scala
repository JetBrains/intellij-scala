package org.jetbrains.plugins.scala.lang.dfa.analysis.tests.invocations

import org.jetbrains.plugins.scala.lang.dfa.Messages._
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase

class InvalidInvocationsDfaTest extends ScalaDfaTestBase {

  def testProperFlushingOfVars(): Unit = test(codeFromMethodBody(returnType = "Boolean") {
    """
      |val x = 333
      |var y = 303
      |anotherMethod(1000 * 3 - 9, x, true, "???")
      |x == 333
      |y == 303
      |anotherMetod(1000 * 3 - 9, x, true, "???")
      |x == 332
      |y == 302 + 1
      |var z = 55
      |anotherMethod(1000 * 3 - 9, x, false, "???", 33)
      |z == 55
      |x == 334 - 1
      |var d = 3
      |d == 15
      |val r = 2 + 7 $ 3 * 8 dd 9
      |r == 3
      |d == 14
      |""".stripMargin
  })(
    "x == 333" -> ConditionAlwaysTrue,
    "x == 332" -> ConditionAlwaysFalse,
    "x == 334 - 1" -> ConditionAlwaysTrue,
    "d == 15" -> ConditionAlwaysFalse
  )
}
