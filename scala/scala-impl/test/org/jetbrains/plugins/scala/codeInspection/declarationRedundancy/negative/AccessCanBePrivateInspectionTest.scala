package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.ScalaHighlightsTestBase
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection

final class AccessCanBePrivateInspectionTest extends ScalaHighlightsTestBase {
  override protected val description = "Access can be private"

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_used_val(): Unit = {
    val code = "private class A { val foo = 42 }; private class B { new A().foo }"
    checkTextHasNoErrors(code)
  }

  def test_used_var(): Unit = {
    val code = "private class A { var foo = 42 }; private class B { new A().foo }"
    checkTextHasNoErrors(code)
  }

  def test_used_method(): Unit = {
    val code = "private class A { def foo = {} }; private class B { new A().foo }"
    checkTextHasNoErrors(code)
  }

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
}
