package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import com.intellij.codeInspection.ex.QuickFixWrapper
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.{MakePrivateQuickFix, ScalaAccessCanBeTightenedInspection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.junit.Assert.assertTrue

/**
 * For the motivation behind this test, see
 * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection#processElement]]
 */

class MakePrivateQuickFixIsAboveAddTypeAnnotationQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = "Access can be private"

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  def test_text_when_add_type_annotation_quickfix_is_offered(): Unit = {
    val code = s"class A { val a = new A }; private class B { val b = new A().a }"
    val fixes = doFindQuickFixes(code, "Add 'private' modifier")

    assertTrue(fixes.size == 1)

    val quickFixElement = fixes.head.asInstanceOf[QuickFixWrapper].getFix.asInstanceOf[MakePrivateQuickFix].getStartElement.asInstanceOf[ScPatternDefinition]
    assert(quickFixElement.declaredNames.head == "b")
  }

  def test_text_when_add_type_annotation_quickfix_is_not_offered(): Unit = {
    val code = s"class A { val a = 42 }"
    val fixes = doFindQuickFixes(code, "Make 'private'")

    assertTrue(fixes.size == 1)

    val quickFixElement = fixes.head.asInstanceOf[QuickFixWrapper].getFix.asInstanceOf[MakePrivateQuickFix].getStartElement.asInstanceOf[ScPatternDefinition]
    assert(quickFixElement.declaredNames.head == "a")
  }
}
