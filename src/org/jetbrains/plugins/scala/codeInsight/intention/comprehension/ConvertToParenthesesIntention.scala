package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForStatement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */

class ConvertToParenthesesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert to parentheses"

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
    val manager = statement.getManager
    val block = ScalaPsiElementFactory.parseElement("(_)", manager)

    for (lBrace <- Option(statement.findFirstChildByType(ScalaTokenTypes.tLBRACE))) {
      lBrace.replace(block.getFirstChild)
    }

    for (rBrace <- Option(statement.findFirstChildByType(ScalaTokenTypes.tRBRACE))) {
      rBrace.replace(block.getLastChild)
    }

    for (enumerators <- statement.enumerators;
         cr <- enumerators.findChildrenByType(TokenType.WHITE_SPACE) if cr.getText.contains('\n')) {
      cr.replace(ScalaPsiElementFactory.createSemicolon(manager))
    }

    for (cr <- statement.findChildrenByType(TokenType.WHITE_SPACE) if cr.getText.contains('\n')) {
      cr.delete()
    }
  }
}