package org.jetbrains.plugins.scala
package codeInsight
package intention
package comprehension

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForStatement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class DesugarForIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert to desugared expression"

  override def getText = "Convert for comprehension to desugared expression"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case e @ Parent(_: ScForStatement) => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val statement = element.getParent.asInstanceOf[ScForStatement]
    statement.getDesugarizedExprText(forDisplay = true) match {
      case Some(expText) =>
        val desugared = ScalaPsiElementFactory.createExpressionWithContextFromText(expText, statement.getContext, statement)
        val result = statement.replace(desugared.copy())
        val manager: CodeStyleManager = CodeStyleManager.getInstance(project)
        manager.reformat(result)
     case None =>
    }
  }
}