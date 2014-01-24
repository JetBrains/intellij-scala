package org.jetbrains.plugins.scala
package codeInsight
package intention
package literal

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import com.intellij.codeInsight.FileModificationService
import com.intellij.openapi.command.undo.UndoUtil
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.format.{InterpolatedStringParser, InterpolatedStringFormatter}

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
    val lit: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (lit == null || !lit.isString) return
    if (!FileModificationService.getInstance.preparePsiElementForWrite(element)) return
    val containingFile = element.getContainingFile
    lit match {
      case interpolated: ScInterpolatedStringLiteral =>
        val prefix = interpolated.reference.map(_.getText).getOrElse("")
        val parts = InterpolatedStringParser.parse(interpolated).getOrElse(Nil)
        val content = InterpolatedStringFormatter.formatContent(parts)
        val quote = if (interpolated.isMultiLineString) "\"" else "\"\"\""
        val text = s"$prefix$quote$content$quote"
        val newLiteral = ScalaPsiElementFactory.createExpressionFromText(text, element.getManager)
        interpolated.replace(newLiteral)
      case _ =>
        lit.getValue match {
          case s: String =>
            val newContent =
              if (lit.isMultiLineString) "\"" + StringUtil.escapeStringCharacters(s) + "\""
              else "\"\"\"" + s + "\"\"\""
            val newString = ScalaPsiElementFactory.createExpressionFromText(newContent, element.getManager)
            lit.replace(newString)
          case _ =>
        }
    }
    UndoUtil.markPsiFileForUndo(containingFile)
  }
}