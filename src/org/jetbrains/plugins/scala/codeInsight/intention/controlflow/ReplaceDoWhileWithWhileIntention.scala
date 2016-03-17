package org.jetbrains.plugins.scala
package codeInsight.intention.controlflow

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.Set

/**
 * Nikolay.Tropin
 * 4/17/13
 */
object ReplaceDoWhileWithWhileIntention {
  def familyName = "Replace do while with while"
}

class ReplaceDoWhileWithWhileIntention extends PsiElementBaseIntentionAction {
  def getFamilyName = ReplaceDoWhileWithWhileIntention.familyName

  override def getText: String = ReplaceDoWhileWithWhileIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    for {
      doStmt <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScDoStmt], false))
      condition <- doStmt.condition
      body <- doStmt.getExprBody
    } {
      val offset = editor.getCaretModel.getOffset
      //offset is on the word "do" or "while"
      if ((offset >= doStmt.getTextRange.getStartOffset && offset < body.getTextRange.getStartOffset) ||
              (offset > body.getTextRange.getEndOffset && offset < condition.getTextRange.getStartOffset))
        return true
    }

    false
  }

  override def invoke(project: Project, editor: Editor, element: PsiElement) {
    //check for name conflicts
    for {
      doStmt <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScDoStmt]))
      body <- doStmt.getExprBody
      doStmtParent <- doStmt.parent
    } {
      implicit val typeSystem = project.typeSystem
      val nameConflict = (declaredNames(body) intersect declaredNames(doStmtParent)).nonEmpty
      if (nameConflict) {
        val message = "This action will cause name conflict."
        showNotification(message)
        return
      }
    }

    doReplacement()

    def showNotification(text: String) {

      val popupFactory = JBPopupFactory.getInstance
      popupFactory.createConfirmation(text, "Continue", "Cancel", new Runnable {
        //action on confirmation
        def run() {
          //to make action Undoable
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            def run() { doReplacement() }
          }, null, null)
        }
      }, 0).showInBestPositionFor(editor)
    }


    def doReplacement() {
      for {
        doStmt <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScDoStmt]))
        condition <- doStmt.condition
        body <- doStmt.getExprBody
        doStmtParent <- doStmt.parent
      } {
        val condText = condition.getText
        val bodyText = body.getText

        val whileText = s"while ($condText) $bodyText"

        val manager = element.getManager

        val newWhileStmt = ScalaPsiElementFactory.createExpressionFromText(whileText.toString, manager)
        val newBody = ScalaPsiElementFactory.createExpressionFromText(bodyText, manager)

        val parentBlockHasBraces: Boolean = doStmt.getParent.children.map(_.getNode.getElementType).contains(ScalaTokenTypes.tLBRACE)

        val parentBlockNeedBraces: Boolean = doStmtParent match {
          case _: ScalaFile => false
          case block: ScBlock => block.getParent match {
            case _: ScCaseClause => false
            case _ => true
          }
          case _ => true
        }

        inWriteAction {
          val newDoStmt =
            if (!parentBlockHasBraces && parentBlockNeedBraces) {
              val doStmtInBraces =
                doStmt.replaceExpression(ScalaPsiElementFactory.createBlockFromExpr(doStmt, manager), removeParenthesis = true)
              PsiTreeUtil.findChildOfType(doStmtInBraces, classOf[ScDoStmt], true)
            } else doStmt
          val newExpression: ScExpression = newDoStmt.replaceExpression(newWhileStmt, removeParenthesis = true)
          val parent = newExpression.getParent

          val bodyElements = newBody match {
            case _: ScBlock => newBody.children
            case _: ScExpression => Iterator(newBody)
          }

          for (elem <- bodyElements) {
            val elementType: IElementType = elem.getNode.getElementType
            if (elementType != ScalaTokenTypes.tLBRACE && elementType != ScalaTokenTypes.tRBRACE)
              parent.addBefore(elem, newExpression)
          }
          parent.addBefore(ScalaPsiElementFactory.createNewLine(manager), newExpression)

          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
    }
  }

  def declaredNames(element: PsiElement)
                   (implicit typeSystem: TypeSystem): Set[String] = {
    val firstChild: PsiElement = element.firstChild.get
    val processor: CompletionProcessor = new CompletionProcessor(StdKinds.refExprLastRef, firstChild, collectImplicits = true)
    element.processDeclarations(processor, ResolveState.initial(), firstChild, firstChild)
    val candidates: Set[ScalaResolveResult] = processor.candidatesS

    candidates.map(_.name)
  }

}