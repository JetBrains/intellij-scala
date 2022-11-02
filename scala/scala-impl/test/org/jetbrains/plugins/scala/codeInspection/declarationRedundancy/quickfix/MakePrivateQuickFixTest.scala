package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection
import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}

class MakePrivateQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  private val hint = ScalaInspectionBundle.message("make.private")

  override protected val description = ScalaInspectionBundle.message("access.can.be.private")

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_method(): Unit = {
    val code = "private class Foo { def bar = {}; bar }"
    val expected = "private class Foo { private def bar = {}; bar }"
    testQuickFix(code, expected, hint)
  }

  def test_val(): Unit = {
    val code = "private class Foo { val bar = 42; bar }"
    val expected = "private class Foo { private val bar = 42; bar }"
    testQuickFix(code, expected, hint)
  }

  def test_var(): Unit = {
    val code = "private class Foo { var bar = 42; bar }"
    val expected = "private class Foo { private var bar = 42; bar }"
    testQuickFix(code, expected, hint)
  }
}
