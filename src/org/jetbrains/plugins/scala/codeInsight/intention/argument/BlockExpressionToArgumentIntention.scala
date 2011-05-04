package org.jetbrains.plugins.scala.codeInsight.intention.argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScArgumentExprList}
import com.intellij.psi.{PsiWhiteSpace, PsiElement}

/**
 * Pavel Fatin
 */

class BlockExpressionToArgumentIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Argument Conversion"

  override def getText = "Convert to argument in parentheses"

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case Both(Parent(block: ScBlockExpr), Parent(Parent(list: ScArgumentExprList)))
        if list.exprs.size == 1 && block.caseClauses.isEmpty => true
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val block = element.getParent.asInstanceOf[ScBlockExpr]
    val s = block.getText
    val text = "foo(%s)".format(s.substring(1, s.length - 1).replaceAll("\n", ""))
    val arguments = ScalaPsiElementFactory.createExpressionFromText(text, block.getManager)
            .children.findByType(classOf[ScArgumentExprList]).get
    val replacement = block.getParent.replace(arguments)
    replacement.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _ =>
    }
  }
}