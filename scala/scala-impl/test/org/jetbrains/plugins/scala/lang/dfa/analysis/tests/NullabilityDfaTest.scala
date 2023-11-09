package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages.{ConditionAlwaysFalse, ConditionAlwaysTrue}
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaNullAccessProblem.npeOnInvocation

class NullabilityDfaTest extends ScalaDfaTestBase {

  def test_always_null(): Unit = test(codeFromMethodBody() {
      """
        |val x: String = null
        |
        |x.toString()
        |
        |if (x != null) {
        |  x.toString()
        |}
        |""".stripMargin
    })(
      "x" -> npeOnInvocation.alwaysMessage,
    "x != null" -> ConditionAlwaysFalse,
    )

  def test_probably_not_null(): Unit = test(codeFromMethodBody() {
    """
      |val x: String = arg4
      |
      |(x).toString()
      |
      |if (x == null) {
      |  x.toString()
      |}
      |
      |if (x != null) {
      |  x.toString()
      |}
      |""".stripMargin
  })(
    "x"-> npeOnInvocation.alwaysMessage,
  )

  def test_never_null(): Unit = test(codeFromMethodBody() {
    """
      |val x: String = ""
      |
      |if (x == null) {
      |  x.toString() // no problem, because this never happens!
      |}
      |
      |if (x != null) {
      |  x.toString()
      |}
      |""".stripMargin
  })(
    "x == null" -> ConditionAlwaysFalse,
    "x != null" -> ConditionAlwaysTrue,
  )

  def test_null_from_branch(): Unit = test(codeFromMethodBody() {
    """
      |val x = if (arg3) "" else null
      |x.toString()
      |""".stripMargin
  })(
    "x" -> npeOnInvocation.sometimesMessage
  )
}
