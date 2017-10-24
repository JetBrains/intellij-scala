package org.jetbrains.plugins.scala
package codeInsight
package intention
package literal

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.format.{Text, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.MultilineStringUtil._

class StringToMultilineStringIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "Regular/Multi-line String conversion"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val literalExpression: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    literalExpression match {
      case null => false
      case lit if lit.isMultiLineString =>
        setText("Convert to \"string\"")
        true
      case lit if lit.isString =>
        setText("Convert to \"\"\"string\"\"\"")
        true
      case _ => false
    }
  }


  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!element.isValid) return
    val lit: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (lit == null || !lit.isString) return
    if (!FileModificationService.getInstance.preparePsiElementForWrite(element)) return
    val containingFile = element.getContainingFile

    if (lit.isMultiLineString) multilineToRegular(lit)
    else regularToMultiline(lit, editor)

    UndoUtil.markPsiFileForUndo(containingFile)
  }

  def regularToMultiline(literal: ScLiteral, editor: Editor) {
    import literal.projectContext

    val document = editor.getDocument
    literal match {
      case interpolated: ScInterpolatedStringLiteral =>
        val prefix = interpolated.reference.map(_.getText).getOrElse("")
        val parts = InterpolatedStringParser.parse(interpolated).getOrElse(Nil)
        val content = InterpolatedStringFormatter.formatContent(parts, toMultiline = true)
        val quote = "\"\"\""
        val text = s"$prefix$quote$content$quote"
        val newLiteral = createExpressionFromText(text)
        val replaced = interpolated.replace(newLiteral)
        addMarginsAndFormatMLString(replaced, document)
      case _ =>
        literal.getValue match {
          case s: String =>
            val newString = createExpressionFromText("\"\"\"" + s.replace("\r", "") + "\"\"\"")
            val replaced = literal.replace(newString)
            addMarginsAndFormatMLString(replaced, document)
          case _ =>
        }
    }
  }

  def multilineToRegular(literal: ScLiteral) {
    implicit val projectContext = literal.projectContext
    literal match {
      case interpolated: ScInterpolatedStringLiteral =>
        val prefix = interpolated.reference.map(_.getText).getOrElse("")
        var toReplace: PsiElement = interpolated
        val parts = literal match {
          case WithStrippedMargin(expr, _) =>
            toReplace = expr
            StripMarginParser.parse(literal).getOrElse(Nil)
          case _ => InterpolatedStringParser.parse(interpolated).getOrElse(Nil)
        }
        val content = InterpolatedStringFormatter.formatContent(parts, toMultiline = false)
        val quote = "\""
        val text = s"$prefix$quote$content$quote"
        val newLiteral = createExpressionFromText(text)
        toReplace.replace(newLiteral)
      case _ =>
        var toReplace: PsiElement = literal
        val parts = literal match {
          case WithStrippedMargin(expr, _) =>
            toReplace = expr
            StripMarginParser.parse(literal).getOrElse(Nil)
          case _ =>
            literal.getValue match {
              case s: String => List(Text(s))
              case _ => Nil
            }
        }
        parts match {
          case Seq(Text(s)) =>
            val newLiteralText = "\"" + StringUtil.escapeStringCharacters(s) + "\""
            val newLiteral = createExpressionFromText(newLiteralText)
            toReplace.replace(newLiteral)
          case _ =>
        }
    }

  }
}
