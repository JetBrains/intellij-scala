package org.jetbrains.plugins.scala
package codeInsight
package intention
package stringLiteral

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.MultilineStringUtil.{hasMarginChars, hasStripMarginCall}

final class AddStripMarginToMLStringIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element.getNode.getElementType match {
      case lang.lexer.ScalaTokenTypes.tMULTILINE_STRING if element.getText.contains("\n") =>
        !hasStripMarginCall(element) && hasMarginChars(element, getMarginChar(project))
      case _ => false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val suffix = getMarginChar(project) match {
      case "|" => ""
      case marginChar => "(\'" + marginChar + "\')"
    }

    inWriteAction {
      editor.getDocument.insertString(element.getTextRange.getEndOffset, ".stripMargin" + suffix)
    }
  }

  override def getFamilyName: String = ScalaCodeInsightBundle.message("family.name.add.stripmargin")

  override def getText: String = ScalaCodeInsightBundle.message("add.stripmargin")

  private def getMarginChar(project: Project): String =
    CodeStyle.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings]).MULTILINE_STRING_MARGIN_CHAR
}
