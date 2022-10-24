package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.quickfix

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBeTightenedInspection
import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionBundle}
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

  def test_text_when_add_type_annotation_quickfix_is_offered(): Unit = {
    val code = s"private class A { val a = new A }; private class B { val b = new A().a }"

    val highlights = configureByText(code).actualHighlights
    assert(highlights.size == 1)
    val (descriptor: HighlightInfo.IntentionActionDescriptor, range: TextRange) = highlights.head.findRegisteredQuickFix((a, b)  => (a, b))
    assert(range == new TextRange(57, 58))
    assertTrue(descriptor.getAction.getText == ScalaInspectionBundle.message("add.private.modifier"))
  }

  def test_text_when_add_type_annotation_quickfix_is_not_offered(): Unit = {
    val code = s"private class A { val a = 42 }"

    val highlights = configureByText(code).actualHighlights
    assert(highlights.size == 1)
    val (descriptor: HighlightInfo.IntentionActionDescriptor, range: TextRange) = highlights.head.findRegisteredQuickFix((a, b) => (a, b))
    assert(range == new TextRange(22, 23))
    assertTrue(descriptor.getAction.getText == ScalaInspectionBundle.message("make.private"))
  }
}
