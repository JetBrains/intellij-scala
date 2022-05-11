package org.jetbrains.plugins.scala
package codeInspection
package internal

import com.intellij.codeInspection.LocalInspectionTool

class ScalaShouldBeTextContainsInspectionTest extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaShouldBeTextContainsInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("internal.replace.with.textContains")

  override def createTestText(text: String): String =
    s"""
      |object com {
      |  object intellij {
      |    object psi {
      |      class PsiElement {
      |        def getText = ""
      |      }
      |    }
      |    class SubPsiElement extends psi.PsiElement
      |
      |    object lang {
      |      class ASTNode {
      |        def getText = ""
      |      }
      |    }
      |  }
      |}
      |
      |object Test {
      |  val psi = new com.intellij.psi.PsiElement
      |  val sub = new com.intellij.SubPsiElement
      |  val ast = new com.intellij.lang.ASTNode
      |  $text
      |}
      |""".stripMargin

  def test_without_parenthesis(): Unit = {
    checkTextHasError(s"""psi.${START}getText.contains('c')$END""")

    val text = s"""psi.getText$CARET.contains('c')"""
    val result = s"""psi.textContains('c')"""
    testQuickFix(text, result, description)
  }

  def test_with_parenthesis(): Unit = {
    checkTextHasError(s"""psi.${START}getText().contains('c')$END""")

    val text = s"""psi.getText()$CARET.contains('c')"""
    val result = s"""psi.textContains('c')"""
    testQuickFix(text, result, description)
  }

  def test_subclass(): Unit =
    checkTextHasError(s"""sub.${START}getText().contains('c')$END""")


  def test_with_astnode(): Unit = {
    checkTextHasError(s"""ast.${START}getText().contains('c')$END""")

    val text = s"""ast.getText()$CARET.contains('c')"""
    val result = s"""ast.textContains('c')"""
    testQuickFix(text, result, description)
  }

  def test_not_with_string(): Unit = checkTextHasNoErrors(
    s"""psi.getText().contains("c")"""
  )
}