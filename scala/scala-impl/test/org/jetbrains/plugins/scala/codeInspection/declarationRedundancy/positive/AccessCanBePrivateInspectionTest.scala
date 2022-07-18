package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.ScalaHighlightsTestBase
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection

class AccessCanBePrivateInspectionTest extends ScalaHighlightsTestBase {

  override protected val description = "Access can be private"

  override def setUp(): Unit = {
      super.setUp()
      myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_unused_method(): Unit = {
    val code = s"private class Foo { def ${START}bar$END = {} }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_used_method(): Unit = {
    val code = s"private class Foo { def ${START}bar$END = {}; bar }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_unused_val(): Unit = {
    val code = s"private class Foo { val ${START}bar$END = 42 }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_used_val(): Unit = {
    val code = s"private class Foo { val ${START}bar$END = 42; bar }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_unused_var(): Unit = {
    val code = s"private class Foo { var ${START}bar$END = 42 }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_used_var(): Unit = {
    val code = s"private class Foo { var ${START}bar$END = 42; bar }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_unused_class(): Unit = {
    val code = s"class ${START}A$END"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_used_class(): Unit = {
    val code = s"class ${START}A$END; private class B"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }


  /**
   * I don't think we can implement the below case without relying on either type resolution or reference checking.
   */

//  def test_class_with_same_name_in_other_package(): Unit = {
//    val file = myFixture.addFileToProject("B.scala", "package b; class A; class B { new A() }")
//    myFixture.openFileInEditor(file.getVirtualFile)
//    val code = s"package a; class ${START}A$END"
//    checkTextHasError(code, allowAdditionalHighlights = false)
//  }

  def test_that_fails_to_prevent_merge(): Unit = throw new Exception
}
