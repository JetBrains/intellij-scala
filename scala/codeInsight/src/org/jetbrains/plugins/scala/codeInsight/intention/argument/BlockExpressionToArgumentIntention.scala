package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */
final class BlockExpressionToArgumentIntention extends PsiElementBaseIntentionAction {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    element match {
      case Parent((block: ScBlockExpr) && Parent(list: ScArgumentExprList))
        if list.exprs.size == 1 && block.caseClauses.isEmpty => IntentionAvailabilityChecker.checkIntention(this, element)
      case _ => false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val block = element.getParent.asInstanceOf[ScBlockExpr]
    val s = block.getText
    val text = s"foo(${s.substring(1, s.length - 1).replaceAll("\n", "")})"
    val arguments = createExpressionFromText(text)(block.getManager)
            .children.instanceOf[ScArgumentExprList].get
    val replacement = block.getParent.replace(arguments)
    replacement.getPrevSibling match {
      case ws: PsiWhiteSpace => ws.delete()
      case _ =>
    }
  }

  override def getFamilyName = "Convert to argument in parentheses"

  override def getText: String = getFamilyName
}