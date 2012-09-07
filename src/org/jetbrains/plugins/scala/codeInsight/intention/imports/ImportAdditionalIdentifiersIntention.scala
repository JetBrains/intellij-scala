package org.jetbrains.plugins.scala
package codeInsight.intention.imports

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import lang.psi.api.base.ScStableCodeReferenceElement
import lang.psi.api.toplevel.imports.ScImportExpr
import com.intellij.psi.{PsiWhiteSpace, PsiDocumentManager, PsiElement}

/**
 * Jason Zaugg
 */

class ImportAdditionalIdentifiersIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Import additional identifiers"

  override def getText = "Import additional identifiers from qualifier"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    check(project, editor, element).isDefined
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    if (!element.isValid) return
    check(project, editor, element) match {
      case Some(x) => x()
      case None =>
    }
  }

  override def startInWriteAction(): Boolean = false

  private def check(project: Project, editor: Editor, element: PsiElement): Option[() => Unit] = {
    element match {
      case ws: PsiWhiteSpace if element.getPrevSibling != null &&
              editor.getCaretModel.getOffset == element.getPrevSibling.getTextRange.getEndOffset =>
        val prev = element.getContainingFile.findElementAt(element.getPrevSibling.getTextRange.getEndOffset - 1)
        return check(project, editor, prev)
      case _ =>
    }
    if (element != null) {
      element.getParent match {
        case id: ScStableCodeReferenceElement if id.nameId == element =>
          id.getParent match {
            case imp: ScImportExpr if imp.selectorSet.isEmpty =>
              val doIt = () => {
                val newExpr = ScalaPsiElementFactory.createImportExprFromText(imp.qualifier.getText + ".{" + id.nameId.getText + "}", element.getManager)
                val replaced = inWriteAction {
                  val replaced = imp.replace(newExpr)
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
    } else {
      None
    }
  }
}
