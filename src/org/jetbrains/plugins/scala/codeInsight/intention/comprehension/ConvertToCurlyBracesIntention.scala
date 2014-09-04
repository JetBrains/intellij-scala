package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScForStatement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Pavel Fatin
 */

class ConvertToCurlyBracesIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = "Convert to curly braces"

  override def getText = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement) = {
    element match {
      case e @ Parent(_: ScForStatement) =>
        List(ScalaTokenTypes.tLPARENTHESIS, ScalaTokenTypes.tRPARENTHESIS).contains(e.getNode.getElementType) &&
          IntentionAvailabilityChecker.checkIntention(this, element)
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    val statement = element.getParent.asInstanceOf[ScForStatement]
    val manager = statement.getManager
    val block = ScalaPsiElementFactory.parseElement("{}", manager)

    for (lParen <- Option(statement.findFirstChildByType(ScalaTokenTypes.tLPARENTHESIS))) {
      val lBrace = lParen.replace(block.getFirstChild)
      statement.addAfter(ScalaPsiElementFactory.createNewLine(manager), lBrace)
    }

    for (rParen <- Option(statement.findFirstChildByType(ScalaTokenTypes.tRPARENTHESIS))) {
      val rBrace = rParen.replace(block.getLastChild)
      statement.addBefore(ScalaPsiElementFactory.createNewLine(manager), rBrace)
    }

    for (enumerators <- statement.enumerators;
         semi <- enumerators.findChildrenByType(ScalaTokenTypes.tSEMICOLON)) {
      semi.replace(ScalaPsiElementFactory.createNewLine(manager))
    }
  }
}