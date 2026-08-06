package org.jetbrains.plugins.scala.codeInsight.intention.comprehension

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intention.elementAndTouchingPrevElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

class ConvertToCurlyBracesIntention extends PsiElementBaseIntentionAction with DumbAware {
  override def getFamilyName: String = ScalaBundle.message("family.name.convert.to.curly.braces")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementAndTouchingPrevElement(editor, element).exists {
      case e @ Parent(_: ScFor) =>
        List(ScalaTokenTypes.tLPARENTHESIS, ScalaTokenTypes.tRPARENTHESIS).contains(e.getNode.getElementType) &&
          IntentionAvailabilityChecker.checkIntention(this, element)
      case _ => false
    }
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    implicit val ctx: ProjectContext = project

    val statement = elementAndTouchingPrevElement(editor, element)
      .collectFirst { case Parent(f: ScFor) => f }
      .head
    val block = createElementFromText[PsiElement]("{}", element)

    for (lParen <- statement.findFirstChildByType(ScalaTokenTypes.tLPARENTHESIS)) {
      val lBrace = lParen.replace(block.getFirstChild)
      statement.addAfter(createNewLine(), lBrace)
    }

    for (rParen <- statement.findFirstChildByType(ScalaTokenTypes.tRPARENTHESIS)) {
      val rBrace = rParen.replace(block.getLastChild)
      statement.addBefore(createNewLine(), rBrace)
    }

    for (enumerators <- statement.enumerators;
         semi <- enumerators.findChildrenByType(ScalaTokenTypes.tSEMICOLON)) {
      semi.replace(createNewLine())
    }
  }
}
