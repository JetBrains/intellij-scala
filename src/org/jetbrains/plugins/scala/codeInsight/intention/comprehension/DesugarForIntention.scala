package org.jetbrains.plugins.scala
package codeInsight
package intention
package comprehension

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import lang.psi.api.expr.ScForStatement
import extensions._
import lang.psi.impl.ScalaPsiElementFactory

class DesugarForIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "For comprehension conversion"

  override def getText = "Convert for comprehension to desugared expression"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case e @ Parent(_: ScForStatement) => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val statement = element.getParent.asInstanceOf[ScForStatement]
    statement.getDesugarisedExprText(forDisplay = true) match {
      case Some(expText) =>
        val desugared = ScalaPsiElementFactory.createExpressionWithContextFromText(expText, statement.getContext, statement)
        statement.replace(desugared.copy())
      case None =>
    }
  }
}