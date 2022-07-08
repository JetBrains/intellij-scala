package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, TokenType}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.comprehension.ConvertToParenthesesIntention._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

object ConvertToParenthesesIntention {
  val FamilyName: String = ScalaBundle.message("intention.for.comprehension.convert.to.parentheses")
}

class ConvertToParenthesesIntention extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = FamilyName

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    element match {
      case e @ Parent(_: ScFor) =>
        List(ScalaTokenTypes.tLBRACE, ScalaTokenTypes.tRBRACE).contains(e.getNode.getElementType) && 
          IntentionAvailabilityChecker.checkIntention(this, element)
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val statement = element.getParent.asInstanceOf[ScFor]
    ScalaPsiUtil.replaceBracesWithParentheses(statement)

    val manager = statement.getManager
    for (enumerators <- statement.enumerators;
         cr <- enumerators.findChildrenByType(TokenType.WHITE_SPACE) if cr.textContains('\n')) {
      cr.replace(ScalaPsiElementFactory.createSemicolon(manager))
    }

    for (cr <- statement.findChildrenByType(TokenType.WHITE_SPACE) if cr.textContains('\n')) {
      cr.delete()
    }
  }
}