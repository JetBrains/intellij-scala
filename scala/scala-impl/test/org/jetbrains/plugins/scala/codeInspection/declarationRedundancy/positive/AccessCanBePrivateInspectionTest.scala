package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class AccessCanBePrivateInspectionTest extends ScalaAccessCanBePrivateInspectionTestBase {

  private val AllowAdditionalHighlights = false

  def test_method(): Unit =
    checkTextHasError(s"private class Foo { def ${START}bar$END = {}; def foo = bar }", AllowAdditionalHighlights)

  def test_val(): Unit =
    checkTextHasError(s"private class Foo { val ${START}bar$END = 42; def foo = bar }", AllowAdditionalHighlights)

  def test_var(): Unit =
    checkTextHasError(s"private class Foo { var ${START}bar$END = 42; def foo = bar }", AllowAdditionalHighlights)

  def test_new_template_definition(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; new B }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_val(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private val c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_var(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private var c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_def_return_type(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_this_qualified_def_return_type(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private[this] def c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_return_type1(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Option[B] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_return_type2(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Option[Seq[B]] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_return_type3(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Either[B, String] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_def_param(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def foo(b: B) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_param1(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Option[B]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_param2(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Option[Seq[B]]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_param3(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Either[B, String]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_def_type_param(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def foo[T <: B]() = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_inheritance(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private class C extends B }", AllowAdditionalHighlights)

  def test_usage_within_companion(): Unit = checkTextHasError(
    s"""private class Foo { val ${START}a$END = 42 }
       |private object Foo { new Foo().a }
       |private class Bar { Bar.b }
       |private object Bar { val ${START}b$END = 42 }
       |""".stripMargin, AllowAdditionalHighlights)

  def test_multiple_val_assignment(): Unit = checkTextHasError(
    s"private object Foo { val ${START}x, y$END = 1; println(x + y) }"
  )

  def test_untupled_val_assignment(): Unit = checkTextHasError(
    s"private object Foo { val $START(x, y)$END = (1, 2); println(x + y) }"
  )

  def test_no_need_to_prevent_escaping_via_rhs_usage(): Unit =
    checkTextHasError(s"object A { object ${START}B$END; def foo() { println(B) } }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_local_method_member(): Unit = checkTextHasError(
    s"object A { class ${START}B$END; def foo() { def bar() = { new B } } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_local_value_member(): Unit = checkTextHasError(
    s"object A { class ${START}B$END; val foo = { val bar = { new B } } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_local_method(): Unit = checkTextHasError(
    s"object A { class ${START}B$END; def foo = { def bar = { Some(new B); () }; bar } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_nested_private_method(): Unit = checkTextHasError(
    s"object A { trait ${START}B$END; object C { private def bar: B = ??? } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor1(): Unit = checkTextHasError(
    s"object A { class ${START}B$END }; class A (b: A.B)",
    AllowAdditionalHighlights
  )
  def test_no_need_to_prevent_escaping_via_primary_constructor2(): Unit = checkTextHasError(
    s"object A { class ${START}B$END }; class A (private val b: A.B)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor3(): Unit = checkTextHasError(
    s"object A { class ${START}B$END }; class A private (b: A.B)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor4(): Unit = checkTextHasError(
    s"object A { class ${START}B$END }; class A private (b: A.B = ???)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor5(): Unit = checkTextHasError(
    s"object A { class ${START}B$END }; class A private (private val b: A.B)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor6(): Unit = checkTextHasError(
    s"object A { class ${START}B$END }; class A private (private val b: A.B = ???)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_type_alias(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private type C = B }", AllowAdditionalHighlights)
}
