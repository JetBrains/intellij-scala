package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection

final class AccessCanBePrivateInspectionTest extends ScalaAnnotatorQuickFixTestBase {
  override protected val description = ScalaInspectionBundle.message("access.can.be.private")

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_val(): Unit =
    checkTextHasNoErrors("private class A { val foo = 42 }; private class B { new A().foo }")

  def test_var(): Unit =
    checkTextHasNoErrors("private class A { var foo = 42 }; private class B { new A().foo }")

  def test_method(): Unit =
    checkTextHasNoErrors("private class A { def foo = {} }; private class B { new A().foo }")

  def test_class_used_by_public_class_in_other_file(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B extends A")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "class A"
    checkTextHasNoErrors(code)
  }

  def test_trait_used_by_public_class_in_other_file(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B extends A")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "trait A"
    checkTextHasNoErrors(code)
  }

  def test_object_used_by_public_class_in_other_file(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B { A }")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "object A"
    checkTextHasNoErrors(code)
  }

  def test_declaration_that_is_used_both_in_local_and_nonlocal_scope(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B extends A { foo() }")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "class A { def foo() = { foo() } }"
    checkTextHasNoErrors(code)
  }

  def test_local_method_members_are_skipped(): Unit =
    checkTextHasNoErrors("private class A { private def foo() = { def bar() = {} } }")

  def test_local_var_val_members_are_skipped(): Unit =
    checkTextHasNoErrors("private class A { private def foo() = { var x = 42; val y = 42; } }")

  def test_unused_declarations_are_skipped(): Unit =
    checkTextHasNoErrors("private class A { def foo() = {} }")

  def test_prevent_leaking_via_val(): Unit =
    checkTextHasNoErrors("object A { class B; val c: B = ??? }")

  def test_prevent_leaking_via_var(): Unit =
    checkTextHasNoErrors("object A { class B; var c: B = ??? }")

  def test_prevent_leaking_via_def_return_type(): Unit =
    checkTextHasNoErrors("object A { class B; def c: B = ??? }")

  def test_prevent_leaking_via_nested_def_return_type1(): Unit =
    checkTextHasNoErrors("object A { class B; def c: Option[B] = ??? }")

  def test_prevent_leaking_via_nested_def_return_type2(): Unit =
    checkTextHasNoErrors("object A { class B; def c: Option[Seq[B]] = ??? }")

  def test_prevent_leaking_via_nested_def_return_type3(): Unit =
    checkTextHasNoErrors("object A { class B; def c: Either[B, String] = ??? }")

  def test_prevent_leaking_via_package_qualified_private_def_return_type(): Unit =
    checkTextHasNoErrors("package a; object A { class B; private[a] def c: B = ??? }")

  def test_prevent_leaking_via_def_param(): Unit =
    checkTextHasNoErrors("object A { class B; def foo(b: B) = () }")

  def test_prevent_leaking_via_nested_def_param1(): Unit =
    checkTextHasNoErrors("object A { class B; def c(d: Option[B]) = () }")

  def test_prevent_leaking_via_nested_def_param2(): Unit =
    checkTextHasNoErrors("object A { class B; def c(d: Option[Seq[B]]) = () }")

  def test_prevent_leaking_via_nested_def_param3(): Unit =
    checkTextHasNoErrors("object A { class B; def c(d: Either[B, String]) = () }")

  def test_prevent_leaking_via_def_type_param(): Unit =
    checkTextHasNoErrors("object A { class B; def foo[T <: B]() = () }")

  def test_prevent_leaking_via_inheritance(): Unit =
    checkTextHasNoErrors("object A { class B; class C extends B }")

  def test_member_of_local_class(): Unit =
    checkTextHasNoErrors("object A { def foo = { class B { val bar = 42; println(bar) } } }")

  def test_member_of_a_surprisingly_nested_local_class(): Unit =
    checkTextHasNoErrors("class A { def foo = { class B { class C { class D { def bar = {}; bar } } } } }")
}
