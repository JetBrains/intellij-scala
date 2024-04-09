package org.jetbrains.plugins.scala.annotator.quickfix

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

class ModifierQuickFixTest extends ScalaLightCodeInsightFixtureTestCase {

  private def doTestRemoveModifierFix(before: String, after: String): Unit = {
    val psiFile = myFixture.configureByText(s"${getTestName(false)}.scala", before)

    val elementAtCaret = psiFile.findElementAt(getEditor.getCaretModel.getOffset)
    val modifiersOwner = elementAtCaret.parentOfType[ScModifierListOwner].get

    val fix = new ModifierQuickFix.Remove(
      modifiersOwner,
      elementAtCaret,
      ScalaModifier.byText(elementAtCaret.getText)
    )

    inWriteCommandAction({
      fix.invoke(getProject, getEditor, getFile)
    })(getProject)

    myFixture.checkResult(after)
  }

  def testRemoveModifierQuickFix_FirstModifier(): Unit = {
    doTestRemoveModifierFix(
      s"""class A {
         |  ${CARET}final protected def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  protected def abstractFoo: String
         |}
         |""".stripMargin
    )
  }

  def testRemoveModifierQuickFix_SecondModifier(): Unit = {
    doTestRemoveModifierFix(
      s"""class A {
         |  protected ${CARET}final def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  protected def abstractFoo: String
         |}
         |""".stripMargin
    )
  }

  def testRemoveModifierQuickFix_SingleModifier(): Unit = {
    doTestRemoveModifierFix(
      s"""class A {
         |  ${CARET}final def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  def abstractFoo: String
         |}
         |""".stripMargin
    )
  }


  def testRemoveModifierQuickFix_DuplicatedPrivateModifier(): Unit = {
    doTestRemoveModifierFix(
      s"""class A {
         |  ${CARET}private private def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  private def abstractFoo: String
         |}
         |""".stripMargin
    )
  }
}