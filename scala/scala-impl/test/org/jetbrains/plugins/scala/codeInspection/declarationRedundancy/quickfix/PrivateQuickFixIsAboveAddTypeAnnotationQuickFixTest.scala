package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.junit.Assert.assertTrue

/**
 * For the motivation behind this test, see
 * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection.quickFixText]]
 */

class PrivateQuickFixIsAboveAddTypeAnnotationQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = ScalaInspectionBundle.message("access.can.be.private")

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  // Commented out because of a change in org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ProblemInfo and InspectionBasedHighlightingPass
  // Fixes are no longer Seq[LocalQuickFixAndIntentionActionOnPsiElement], but Seq[LocalQuickFix]

  def test_text_when_add_type_annotation_quickfix_is_offered(): Unit = {
    val code = s"class A { val a = new A }; private class B { val b = new A().a }"
    val fixes = doFindQuickFixes(code, ScalaInspectionBundle.message("add.private.modifier"))

    assertTrue(fixes.size == 1)

    val quickFixElement = fixes.head.asInstanceOf[ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix]
      .getStartElement.asInstanceOf[ScPatternDefinition]
    assert(quickFixElement.declaredNames.head == "b")
  }

  def test_text_when_add_type_annotation_quickfix_is_not_offered(): Unit = {
    val code = s"class A { val a = 42 }"
    val fixes = doFindQuickFixes(code, ScalaInspectionBundle.message("make.private"))

    assertTrue(fixes.size == 1)

    val quickFixElement = fixes.head.asInstanceOf[ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix]
      .getStartElement.asInstanceOf[ScPatternDefinition]
    assert(quickFixElement.declaredNames.head == "a")
  }
}
