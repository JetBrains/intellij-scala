package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection

class AccessCanBePrivateInspectionTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = ScalaInspectionBundle.message("access.can.be.private")

  override def setUp(): Unit = {
      super.setUp()
      myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_method(): Unit = {
    val code = s"private class Foo { def ${START}bar$END = {}; bar }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_val(): Unit = {
    val code = s"private class Foo { val ${START}bar$END = 42; bar }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }

  def test_var(): Unit = {
    val code = s"private class Foo { var ${START}bar$END = 42; bar }"
    checkTextHasError(code, allowAdditionalHighlights = false)
  }
}
