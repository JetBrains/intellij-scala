package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection

class MakePrivateQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = "Access can be private"

  /**
   * For the reason behind this plurality of hints see
   * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection#processElement]]
   */
  private val hintWhenAddTypeAnnotationQuickFixIsNotOffered = "Make 'private'"
  private val hintWhenAddTypeAnnotationQuickFixIsOffered = "Add 'private' modifier"

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_unused_method(): Unit = {
    val code = s"private class Foo { def bar = {} }"
    val expected = s"private class Foo { private def bar = {} }"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsOffered)
  }

  def test_used_method(): Unit = {
    val code = s"private class Foo { def bar = {}; bar }"
    val expected = s"private class Foo { private def bar = {}; bar }"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsOffered)
  }

  def test_unused_val(): Unit = {
    val code = s"private class Foo { val bar = 42 }"
    val expected = s"private class Foo { private val bar = 42 }"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsNotOffered)
  }

  def test_used_val(): Unit = {
    val code = s"private class Foo { val bar = 42; bar }"
    val expected = s"private class Foo { private val bar = 42; bar }"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsNotOffered)
  }

  def test_unused_var(): Unit = {
    val code = s"private class Foo { var bar = 42 }"
    val expected = s"private class Foo { private var bar = 42 }"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsOffered)
  }

  def test_used_var(): Unit = {
    val code = s"private class Foo { var bar = 42; bar }"
    val expected = s"private class Foo { private var bar = 42; bar }"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsOffered)
  }

  def test_unused_class(): Unit = {
    val code = s"class A"
    val expected = s"private class A"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsOffered)
  }

  def test_unused_trait(): Unit = {
    val code = s"trait A"
    val expected = s"private trait A"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsOffered)
  }

  def test_unused_object(): Unit = {
    val code = s"object A"
    val expected = s"private object A"
    testQuickFix(code, expected, hintWhenAddTypeAnnotationQuickFixIsOffered)
  }
}
