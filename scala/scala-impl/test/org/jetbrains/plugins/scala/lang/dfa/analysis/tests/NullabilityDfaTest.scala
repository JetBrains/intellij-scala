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
      |def test(@Nullable x: String) {
      |  (x).toString()
      |
      |  if (x == null) {
      |    x.trim()
      |  }
      |
      |  if (x != null) {
      |    x.stripMargin()
      |  }
      |}
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
    "x == null" -> ConditionAlwaysFalse,
    "x != null" -> ConditionAlwaysTrue,
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
  def test_nullable_parameter(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable s: String): Unit = {
      |  s.toString()
      |}
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
  )

  @Test
  def test_nullable_parameter_null_guard(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable s: String): Unit = {
      |  if (s == null) return
      |  s.toString()
      |}
      |""".stripMargin
  })()

  @Test
  def test_nullable_parameter_multiple(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable a: String, @Nullable b: String): Unit = {
      |  a.toString()
      |  b.toString()
      |}
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
    "toString" -> npeOnInvocation.sometimesMessage,
  )

  @Test
  def test_nullable_class_parameter(): Unit = test(codeFromMethodBody() {
    """
      |class Wrapper(@Nullable val value: String)
      |val w = new Wrapper(arg4)
      |w.value.toString()
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
  )

  @Test
  def test_nullable_case_class_parameter(): Unit = test(codeFromMethodBody() {
    """
      |case class Wrapper(@Nullable value: String)
      |val w = Wrapper(arg4)
      |w.value.toString()
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
  )

  @Test
  def test_nullable_var(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable s: String): Unit = {
      |  var x: String = s
      |  x.toString()
      |}
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
  )

  @Test
  def test_nullable_def_return(): Unit = test(codeFromMethodBody() {
    """
      |@Nullable def getNullable(): String = arg4
      |val s = getNullable()
      |s.toString()
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
  )

  @Test
  def test_nullable_def_return_with_guard(): Unit = test(codeFromMethodBody() {
    """
      |@Nullable def getNullable(): String = arg4
      |val s = getNullable()
      |if (s != null) {
      |  s.toString()
      |}
      |""".stripMargin
  })()

  @Test
  def test_not_nullable_parameter(): Unit = test(codeFromMethodBody() {
    """
      |def foo(s: String): Unit = {
      |  s.toString()
      |}
      |""".stripMargin
  })()

  @Test
  def test_assert_not_null(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable s: String): Unit = {
      |  assert(s != null)
      |  s.toString()
      |}
      |foo(arg4)
      |""".stripMargin
  })()

  @Test
  def test_require_not_null(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable s: String): Unit = {
      |  require(s != null)
      |  s.toString()
      |}
      |""".stripMargin
  })()

  @Test
  def test_assert_on_always_null(): Unit = test(codeFromMethodBody() {
    """
      |val x: String = null
      |assert(x != null)
      |x.toString()
      |""".stripMargin
  })(
    "x != null" -> ConditionAlwaysFalse,
  )

  @Test
  def test_objects_requireNonNull(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable s: String): Unit = {
      |  val safe = java.util.Objects.requireNonNull(s)
      |  safe.toString()
      |  s.stripMargin
      |}
      |foo(arg4)
      |""".stripMargin
  })()

  @Test
  def test_contract_fail_on_null_narrows_argument(): Unit = test(codeFromMethodBody() {
    """
      |@Contract("null -> fail")
      |def checkNotNull(@Nullable s: String): Unit = {
      |  if (s == null) throw new IllegalArgumentException
      |}
      |
      |def foo(@Nullable s: String): Unit = {
      |  checkNotNull(s)
      |  s.toString()
      |}
      |""".stripMargin
  })()

  @Test
  def test_contract_fail_on_null_return_value(): Unit = test(codeFromMethodBody() {
    """
      |@Contract("null -> fail; !null -> param1")
      |def requireNotNull(@Nullable s: String): String = {
      |  if (s == null) throw new IllegalArgumentException
      |  s
      |}
      |def foo(@Nullable s: String): Unit = {
      |  val safe = requireNotNull(s)
      |  safe.toString()
      |  s.stripMargin
      |}
      |foo(arg4)
      |""".stripMargin
  })()

  @Test
  def test_contract_fail_and_not_null_return(): Unit = test(codeFromMethodBody() {
    """
      |@Contract("null -> fail; !null -> !null")
      |def ensureNotNull(@Nullable s: String): String = {
      |  if (s == null) throw new IllegalArgumentException
      |  s
      |}
      |def foo(@Nullable s: String): Unit = {
      |  val safe = ensureNotNull(s)
      |  safe.toString()
      |  s.stripMargin
      |}
      |foo(arg4)
      |""".stripMargin
  })()

  @Test
  def test_assert_without_null_check(): Unit = test(codeFromMethodBody() {
    """
      |def foo(@Nullable s: String): Unit = {
      |  s.toString()
      |}
      |""".stripMargin
  })(
    "toString" -> npeOnInvocation.sometimesMessage,
  )

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

  @Test
  def test(): Unit = test(codeFromMethodBody() {
    """
      |@Contract("!null -> param1")
      |def ensureNotNull(@Nullable s: String): String = {
      |  if (s == null) "" else s
      |}
      |
      |def test(@NotNull x: String): Boolean = {
      |  val safe = ensureNotNull(x)
      |
      |  safe == x
      |  safe == null
      |  x == null
      |}
      |""".stripMargin
  })(
    "safe == x" -> ConditionAlwaysTrue,
    "safe == null" -> ConditionAlwaysFalse,
    "x == null" -> ConditionAlwaysFalse,
  )
}
