package org.jetbrains.plugins.scala
package codeInsight.intention.recursion

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi.{PsiFile, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import lang.psi.types.result.TypingContext

/**
 * Pavel Fatin
 */

class AddTailRecursionAnnotationIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Recursion"

  override def getText = "Add @tailrec annotation"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = element match {
    case it @ Parent(f: ScFunctionDefinition) if f.nameId == it =>
      !hasTailRecursionAnnotation(f) && f.recursionType == RecursionType.TailRecursion
    case _ => false
  }

  private def hasTailRecursionAnnotation(f: ScFunctionDefinition): Boolean =
    f.annotations.exists(_.typeElement.getType(TypingContext.empty)
            .map(_.canonicalText).filter(_ == "_root_.scala.annotation.tailrec").isDefined)

  override def invoke(project: Project, editor: Editor, file: PsiFile) {
    val f = file.findElementAt(editor.getCaretModel.getOffset).getParent.asInstanceOf[ScFunctionDefinition]
    f.addAnnotation("scala.annotation.tailrec")
  }
}