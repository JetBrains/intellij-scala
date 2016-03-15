package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForStatement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker
import ConvertToParenthesesIntention._
import org.jetbrains.plugins.scala.ScalaBundle

/**
 * Pavel Fatin
 */

object ConvertToParenthesesIntention {
  val FamilyName: String = ScalaBundle.message("intention.for.comprehension.convert.to.parentheses")
}

class ConvertToParenthesesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = FamilyName

  override def getText = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case e @ Parent(_: ScForStatement) =>
        List(ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE).contains(e.getNode.getElementType) && 
          IntentionAvailabilityChecker.checkIntention(this, element)
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val statement = element.getParent.asInstanceOf[ScForStatement]
    ScalaPsiUtil.replaceBracesWithParentheses(statement)

    val manager = statement.getManager
    for (enumerators <- statement.enumerators;
         cr <- enumerators.findChildrenByType(TokenType.WHITE_SPACE) if cr.getText.contains('\n')) {
      cr.replace(ScalaPsiElementFactory.createSemicolon(manager))
    }

    for (cr <- statement.findChildrenByType(TokenType.WHITE_SPACE) if cr.getText.contains('\n')) {
      cr.delete()
    }
  }
}