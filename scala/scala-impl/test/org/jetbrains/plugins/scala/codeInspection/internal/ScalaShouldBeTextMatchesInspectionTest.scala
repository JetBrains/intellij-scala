package org.jetbrains.plugins.scala.codeInspection.internal

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class ScalaShouldBeTextMatchesInspectionTest extends ScalaInspectionTestBase {

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaShouldBeTextMatchesInspection]

  override protected val description: String =
    ScalaInspectionBundle.message("internal.replace.with.textMatches")

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
    checkTextHasError(s"""psi.${START}getText == "str"$END""")

    val text = s"""psi.getText$CARET == "str"  """
    val result = s"""psi.textMatches("str")  """
    testQuickFix(text, result, description)
  }

  def test_with_parenthesis(): Unit = {
    checkTextHasError(s"""psi.${START}getText() == "str"$END """)

    val text = s"""psi.getText()$CARET == "str"  """
    val result = s"""psi.textMatches("str")  """
    testQuickFix(text, result, description)
  }

  def test_with_parenthesis_in_arg(): Unit = {
    checkTextHasError(s"""psi.${START}getText == ("str")$END """)

    val text = s"""psi.getText$CARET == ("str")  """
    val result = s"""psi.textMatches("str")  """
    testQuickFix(text, result, description)
  }

  def test_flipped(): Unit = {
    checkTextHasError(s"""$START"str" == psi.getText$END""")

    val text = s""" "str" $CARET== psi.getText"""
    val result = s"""psi.textMatches("str")"""
    testQuickFix(text, result, description)
  }

  def test_subclass(): Unit =
    checkTextHasError(s"""sub.${START}getText() == "str"$END """)


  def test_with_astnode(): Unit = checkTextHasNoErrors(
    s"""ast.getText() == "c" """
  )

  def test_negation(): Unit = {
    checkTextHasError(s"""psi.${START}getText != "str"$END """)

    val text = s"""psi.getText$CARET != "str"  """
    val result = s"""!psi.textMatches("str")  """
    testQuickFix(text, result, description)
  }
}
