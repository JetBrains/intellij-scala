package org.jetbrains.plugins.scala
package codeInsight.intention.literal

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.util.MultilineStringUtil

/**
 * User: Dmitry Naydanov
 * Date: 4/2/12
 */

class AddStripMarginToMLStringIntention extends PsiElementBaseIntentionAction{
  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (element == null || element.getNode == null || element.getNode.getElementType != ScalaTokenTypes.tMULTILINE_STRING ||
            !element.getText.contains("\n")) return false

    MultilineStringUtil.needAddStripMargin(element, getMarginChar(project))
  }
  def getFamilyName: String = "Add .stripMargin"

  override def getText: String = "Add 'stripMargin'"

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val marginChar = getMarginChar(project)
    val suffix = if (marginChar == "|") "" else "(\'" + marginChar + "\')"

    extensions.inWriteAction {
      editor.getDocument.insertString(element.getTextRange.getEndOffset, ".stripMargin" + suffix)
    }
  }

  private def getMarginChar(project: Project): String =
    CodeStyle.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings]).MARGIN_CHAR + ""
}
