package org.jetbrains.plugins.scala.lang.dfa.analysis.tests

import org.jetbrains.plugins.scala.lang.dfa.Messages.{ConditionAlwaysFalse, ConditionAlwaysTrue}
import org.jetbrains.plugins.scala.lang.dfa.analysis.ScalaDfaTestBase
import org.jetbrains.plugins.scala.lang.dfa.analysis.framework.ScalaNullAccessProblem.{npeOnInvocation, nullableToUnannotatedParam}
import org.junit.Test

class NullabilityDfaTest extends ScalaDfaTestBase {

  @Test
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
      "toString" -> npeOnInvocation.alwaysMessage,
    "x != null" -> ConditionAlwaysFalse,
    )

  @Test
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
    "toString"-> npeOnInvocation.alwaysMessage,
  )

  @Test
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

  @Test
  def test_null_from_branch(): Unit = test(codeFromMethodBody() {
    """
      |val x = if (arg3) "" else null
      |x.toString()
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage
  )

  @Test
  def test_implicit_class(): Unit = test(codeFromMethodBody() {
    """
      |implicit class TestClass(val x: String) {
      |  def blub(): Unit = ()
      |}
      |
      |val x: String = null
      |x.blub()
      |""".stripMargin
  })(
    "x" -> nullableToUnannotatedParam.alwaysMessage
  )

  @Test
  def test_nullable_implicit_class(): Unit = test(codeFromMethodBody() {
    """
      |implicit class TestClass(@Nullable val x: String) {
      |  def blub(): Unit = ()
      |}
      |
      |val x: String = null
      |x.blub()
      |""".stripMargin
  })()

  @Test
  def test_implicit_conversion(): Unit = test(codeFromMethodBody() {
    """
      |class TestClass(val x: String) {
      |  def blub(): Unit = ()
      |}
      |
      |implicit def toTestClass(x: String): TestClass = new TestClass(x)
      |
      |val x: String = null
      |x.blub()
      |""".stripMargin
  })(
    "x" -> nullableToUnannotatedParam.alwaysMessage
  )
}
