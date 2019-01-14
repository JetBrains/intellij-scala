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
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.collection.Set

/**
 * Nikolay.Tropin
 * 4/17/13
 */
object ReplaceDoWhileWithWhileIntention {
  def familyName = "Replace do while with while"
}

class ReplaceDoWhileWithWhileIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = ReplaceDoWhileWithWhileIntention.familyName

  override def getText: String = ReplaceDoWhileWithWhileIntention.familyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    for {
      doStmt <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScDo], false))
      condition <- doStmt.condition
      body <- doStmt.body
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
    implicit val ctx: ProjectContext = project
    //check for name conflicts
    for {
      doStmt <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScDo]))
      body <- doStmt.body
      doStmtParent <- doStmt.parent
    } {
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
        doStmt <- Option(PsiTreeUtil.getParentOfType(element, classOf[ScDo]))
        condition <- doStmt.condition
        body <- doStmt.body
        doStmtParent <- doStmt.parent
      } {
        val bodyText = body.getText

        val newWhileStmt = createExpressionFromText(s"while (${condition.getText}) $bodyText")
        val newBody = createExpressionFromText(bodyText)

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
                doStmt.replaceExpression(createBlockFromExpr(doStmt), removeParenthesis = true)
              PsiTreeUtil.findChildOfType(doStmtInBraces, classOf[ScDo], true)
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
          parent.addBefore(createNewLine(), newExpression)

          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
    }
  }

  def declaredNames(element: PsiElement): Set[String] = {
    implicit val ctx: ProjectContext = element

    val firstChild: PsiElement = element.getFirstChild
    val processor = new CompletionProcessor(StdKinds.refExprLastRef, firstChild, isImplicit = true)
    element.processDeclarations(processor, ResolveState.initial(), firstChild, firstChild)
    val candidates: Set[ScalaResolveResult] = processor.candidatesS

    candidates.map(_.name)
  }

}