package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{ScalaAccessCanBePrivateInspectionTestBase, ScalaAccessCanBeTightenedInspection}

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

  def test_no_need_to_prevent_leaking_via_val(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private val c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_var(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private var c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_def_return_type(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_this_qualified_def_return_type(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private[this] def c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_nested_def_return_type1(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Option[B] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_nested_def_return_type2(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Option[Seq[B]] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_nested_def_return_type3(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Either[B, String] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_def_param(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def foo(b: B) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_nested_def_param1(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Option[B]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_nested_def_param2(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Option[Seq[B]]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_nested_def_param3(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Either[B, String]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_def_type_param(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def foo[T <: B]() = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_leaking_via_inheritance(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private class C extends B }", AllowAdditionalHighlights)

  def test_usage_within_companion(): Unit = checkTextHasError(
    s"""private class Foo { val ${START}a$END = 42 }
       |private object Foo { new Foo().a }
       |private class Bar { Bar.b }
       |private object Bar { val ${START}b$END = 42 }
       |""".stripMargin, AllowAdditionalHighlights)
}
