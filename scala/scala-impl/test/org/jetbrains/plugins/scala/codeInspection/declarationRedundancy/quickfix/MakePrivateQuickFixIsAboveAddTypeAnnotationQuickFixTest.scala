package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFixAsIntentionAdapter
import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.junit.Assert.assertTrue

/**
 * For the motivation behind this test, see
 * [[org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection.quickFixText]]
 */

class MakePrivateQuickFixIsAboveAddTypeAnnotationQuickFixTest extends ScalaAnnotatorQuickFixTestBase {

  override protected val description = ScalaInspectionBundle.message("access.can.be.private")

  override def setUp(): Unit = {
    super.setUp()
    myFixture.enableInspections(classOf[ScalaAccessCanBeTightenedInspection])
  }

  private def unwrapFix(intentionAction: IntentionAction): ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix = {
    val wrappedFix = intentionAction.asInstanceOf[LocalQuickFixAsIntentionAdapter]
    val myFixField = wrappedFix.getClass.getDeclaredField("myFix")
    myFixField.setAccessible(true)
    myFixField.get(wrappedFix).asInstanceOf[ScalaAccessCanBeTightenedInspection.MakePrivateQuickFix]
  }

  def test_text_when_add_type_annotation_quickfix_is_offered(): Unit = {
    val code = s"class A { val a = new A }; private class B { val b = new A().a }"
    val fixes = doFindQuickFixes(code, ScalaInspectionBundle.message("add.private.modifier"))

    assertTrue(fixes.size == 1)

    val makePrivateQuickFix = unwrapFix(fixes.head)

    val quickFixElement = makePrivateQuickFix.getStartElement.asInstanceOf[ScPatternDefinition]
    assert(quickFixElement.declaredNames.head == "b")
  }

  def test_text_when_add_type_annotation_quickfix_is_not_offered(): Unit = {
    val code = s"class A { val a = 42 }"
    val fixes = doFindQuickFixes(code, ScalaInspectionBundle.message("make.private"))

    assertTrue(fixes.size == 1)

    val makePrivateQuickFix = unwrapFix(fixes.head)

    val quickFixElement = makePrivateQuickFix.getStartElement.asInstanceOf[ScPatternDefinition]
    assert(quickFixElement.declaredNames.head == "a")
  }
}
