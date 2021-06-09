package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createImportExprWithContextFromText

import scala.annotation.tailrec

/**
  * Jason Zaugg
  */

class ImportAdditionalIdentifiersIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = ScalaBundle.message("family.name.import.additional.identifiers")

  override def getText: String = ScalaBundle.message("import.additional.identifiers.from.qualifier")

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    check(project, editor, element).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    if (!element.isValid) return
    check(project, editor, element).foreach(_.apply())
  }

  override def startInWriteAction(): Boolean = false

  @tailrec
  private def check(project: Project, editor: Editor, element: PsiElement): Option[() => Unit] = {
    element match {
      case _: PsiWhiteSpace if element.getPrevSibling != null &&
        editor.getCaretModel.getOffset == element.getPrevSibling.getTextRange.getEndOffset =>
        val prev = element.getContainingFile.findElementAt(element.getPrevSibling.getTextRange.getEndOffset - 1)
        check(project, editor, prev)
      case null => None
      case ChildOf(id: ScStableCodeReference) if id.nameId == element =>
        id.getParent match {
          case imp@ScImportExpr.qualifier(qualifier) if imp.selectorSet.isEmpty =>
            val doIt = () => {
              val name = s"${qualifier.getText}.{${id.nameId.getText}}"

              val replaced = inWriteAction {
                val replaced = imp.replace(createImportExprWithContextFromText(name, element))
                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
                replaced
              }
              inWriteAction {
                editor.getDocument.insertString(replaced.getTextRange.getEndOffset - 1, ", ")
                editor.getCaretModel.moveToOffset(replaced.getTextRange.getEndOffset + 1)
              }
            }
            Some(doIt)
          case _ => None
        }
      case _ => None
    }
  }
}
