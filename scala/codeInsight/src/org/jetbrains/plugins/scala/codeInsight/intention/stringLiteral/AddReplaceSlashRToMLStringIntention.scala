package org.jetbrains.plugins.scala
package codeInsight
package intention
package stringLiteral

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
  * User: Dmitry Naydanov
  * Date: 4/2/12
  */
final class AddReplaceSlashRToMLStringIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element.getNode.getElementType match {
      case lang.lexer.ScalaTokenTypes.tMULTILINE_STRING if element.getText.contains("\n") =>
        import util.MultilineStringUtil._
        val calls = findAllMethodCallsOnMLString(element, "replace")
        !containsArgs(calls, """"\r"""", "\"\"")
      case _ => false
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = inWriteAction {
    editor.getDocument.insertString(element.getTextRange.getEndOffset, ".replace(\"\\r\", \"\")")
  }

  override def getFamilyName: String = """Add .replace("\r","")"""

  override def getText: String = "Add 'replace(\"\\r\", \"\")'"
}

