package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection

class AccessCanBePrivateInspectionTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = ScalaInspectionBundle.message("access.can.be.private")

  private val AllowAdditionalHighlights = false

  override def setUp(): Unit = {
      super.setUp()
      myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_method(): Unit = {
    val code = s"private class Foo { def ${START}bar$END = {}; bar }"
    checkTextHasError(code, AllowAdditionalHighlights)
  }

  def test_val(): Unit = {
    val code = s"private class Foo { val ${START}bar$END = 42; bar }"
    checkTextHasError(code, AllowAdditionalHighlights)
  }

  def test_var(): Unit = {
    val code = s"private class Foo { var ${START}bar$END = 42; bar }"
    checkTextHasError(code, AllowAdditionalHighlights)
  }

  def test_new_template_definition(): Unit = {
    val code = s"object A { class ${START}B$END; new B }"
    checkTextHasError(code, AllowAdditionalHighlights)
  }

  def test_no_need_to_prevent_leaking_via_val(): Unit = {
    checkTextHasError(s"object A { class ${START}B$END; private val c: B = ??? }", AllowAdditionalHighlights)
  }

  def test_no_need_to_prevent_leaking_via_var(): Unit = {
    checkTextHasError(s"object A { class ${START}B$END; private var c: B = ??? }", AllowAdditionalHighlights)
  }

  def test_no_need_to_prevent_leaking_via_def_return_type(): Unit = {
    checkTextHasError(s"object A { class ${START}B$END; private def c: B = ??? }", AllowAdditionalHighlights)
  }

  def test_no_need_to_prevent_leaking_via_def_param(): Unit = {
    checkTextHasError(s"object A { class ${START}B$END; private def foo(b: B) = () }", AllowAdditionalHighlights)
  }

  def test_no_need_to_prevent_leaking_via_def_type_param(): Unit = {
    checkTextHasError(s"object A { class ${START}B$END; private def foo[T <: B]() = () }", AllowAdditionalHighlights)
  }

  def test_no_need_to_prevent_leaking_via_inheritance(): Unit = {
    checkTextHasError(s"object A { class ${START}B$END; private class C extends B }", AllowAdditionalHighlights)
  }
}
