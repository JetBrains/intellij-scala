package org.jetbrains.plugins.scala
package codeInsight
package intention
package literal

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import java.lang.String
import lang.psi.api.base.ScLiteral
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.command.undo.UndoUtil
import lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class StringToMultilineStringIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = "String Conversion"

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    val literalExpression: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (literalExpression == null) {
      false
    } else if (literalExpression.isMultiLineString) {
      setText("Convert to \"string\"")
      true
    } else if (literalExpression.isString) {
      setText("Convert to \"\"\"string\"\"\"")
      true
    } else false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val lit: ScLiteral = PsiTreeUtil.getParentOfType(element, classOf[ScLiteral], false)
    if (lit == null || !lit.isString) return

    lit.getValue match {
      case s: String =>
        if (!CodeInsightUtilBase.preparePsiElementForWrite(element)) return
        val newContent = if (lit.isMultiLineString) {
          "\"" + StringUtil.escapeStringCharacters(s) + "\""
        } else {
          "\"\"\"" + s + "\"\"\""
        }
        val newString = ScalaPsiElementFactory.createExpressionFromText(newContent, element.getManager)
        lit.replace(newString)
        UndoUtil.markPsiFileForUndo(newString.getContainingFile)
      case _ =>
    }
  }
}