package org.jetbrains.plugins.scala.annotator.quickfix

import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

class ModifierQuickFixTest extends ScalaLightCodeInsightFixtureTestCase {
  private def doTestWithModifier(makeQuickfix: PsiFile => ModifierQuickFix)
                                (before: String, after: String): Unit = {
    val psiFile = myFixture.configureByText(s"${getTestName(false)}.scala", before)

    val fix = makeQuickfix(psiFile)

    inWriteCommandAction({
      fix.asIntention().invoke(getProject, getEditor, getFile)
    })(getProject)

    myFixture.checkResult(after)
  }

  private def doTestRemoveModifierFix(before: String, after: String): Unit = {
    doTestWithModifier(file => {
      val elementAtCaret = file.findElementAt(getEditor.getCaretModel.getOffset)
      val modifiersOwner = elementAtCaret.parentOfType[ScModifierListOwner].get
      new ModifierQuickFix.Remove(
        modifiersOwner,
        elementAtCaret,
        ScalaModifier.byText(elementAtCaret.getText)
      )
    })(before, after)
  }

  private def doTestAddModifierFix(before: String, after: String, modifier: String): Unit =
    doTestWithModifier(file => {
      val elementAtCaret = file.findElementAt(getEditor.getCaretModel.getOffset)
      val modifiersOwner = elementAtCaret.parentOfType[ScModifierListOwner].get
      new ModifierQuickFix.Add(modifiersOwner, null, ScalaModifier.byText(modifier))
    })(before, after)

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

  def testAddModifierQuickFix_FirstModifier(): Unit = {
    doTestAddModifierFix(
      s"""class A {
         |  ${CARET}def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  final def abstractFoo: String
         |}
         |""".stripMargin,
      "final"
    )
  }

  def testAddModifierQuickFix_alreadyExisting(): Unit = {
    doTestAddModifierFix(
      s"""class A {
         |  final ${CARET}def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  final def abstractFoo: String
         |}
         |""".stripMargin,
      "final"
    )
  }

  def testAddModifierQuickFix_additional(): Unit = {
    doTestAddModifierFix(
      s"""class A {
         |  final ${CARET}def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  override final def abstractFoo: String
         |}
         |""".stripMargin,
      "override"
    )
  }

  def testAddModifierQuickFix_replace_protected_with_private(): Unit = {
    doTestAddModifierFix(
      s"""class A {
         |  protected ${CARET}def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  private def abstractFoo: String
         |}
         |""".stripMargin,
      "private"
    )
  }

  def testAddModifierQuickFix_replace_private_with_protected(): Unit = {
    doTestAddModifierFix(
      s"""class A {
         |  private ${CARET}def abstractFoo: String
         |}
         |""".stripMargin,
      s"""class A {
         |  protected def abstractFoo: String
         |}
         |""".stripMargin,
      "protected"
    )
  }
}