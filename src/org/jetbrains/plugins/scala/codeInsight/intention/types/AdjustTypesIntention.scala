package org.jetbrains.plugins.scala
package codeInsight.intention.types

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

/**
 * Nikolay.Tropin
 * 2014-08-10
 */
class AdjustTypesIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName = "Adjust types"

  override def getText = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    editor.getSelectionModel.hasSelection &&
            element.getContainingFile.isInstanceOf[ScalaFile] &&
            PsiTreeUtil.getParentOfType(element, classOf[ScImportExpr]) == null
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val file = element.getContainingFile match {
      case sf: ScalaFile => sf
      case _ => return
    }
    val elements = ScalaRefactoringUtil.selectedElements(editor, file, trimComments = true)
    elements.foreach(ScalaPsiUtil.adjustTypes(_))
  }
}
