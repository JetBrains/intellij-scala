package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.typeChecking.IsInstanceOfCall
import org.jetbrains.plugins.scala.codeInspection.typeChecking.TypeCheckToMatchUtil._
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScIfStmt, ScMatchStmt}
import org.jetbrains.plugins.scala.lang.refactoring.util.InplaceRenameHelper


/**
 * Nikolay.Tropin
 * 5/16/13
 */

object ReplaceTypeCheckWithMatchIntention {
  def familyName = "Replace type check with pattern matching"
}

class ReplaceTypeCheckWithMatchIntention extends PsiElementBaseIntentionAction {
  def getFamilyName: String = ReplaceTypeCheckWithMatchIntention.familyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    for {
      IsInstanceOfCall(iioCall) <- element.parentOfType(classOf[ScGenericCall], strict = false)
      ifStmt <- iioCall.parentOfType(classOf[ScIfStmt])
      condition <- ifStmt.condition
      if findIsInstanceOfCalls(condition, onlyFirst = false) contains iioCall
    } {
      val offset = editor.getCaretModel.getOffset
      if (offset >= iioCall.getTextRange.getStartOffset && offset <= iioCall.getTextRange.getEndOffset)
        return true
    }
    false
  }

  def invoke(project: Project, editor: Editor, element: PsiElement) {
    for {
      IsInstanceOfCall(iioCall) <- element.parentOfType(classOf[ScGenericCall], strict = false)
      ifStmt <- iioCall.parentOfType(classOf[ScIfStmt])
      condition <- ifStmt.condition
      if findIsInstanceOfCalls(condition, onlyFirst = false) contains iioCall
    } {
      val (matchStmtOption, renameData) = buildMatchStmt(ifStmt, iioCall, onlyFirst = false)(project)
      for (matchStmt <- matchStmtOption) {
        val newMatch = inWriteAction {
          ifStmt.replaceExpression(matchStmt, removeParenthesis = true).asInstanceOf[ScMatchStmt]
        }
        if (!ApplicationManager.getApplication.isUnitTestMode) {
          val renameHelper = new InplaceRenameHelper(newMatch)
          setElementsForRename(newMatch, renameHelper, renameData)
          renameHelper.startRenaming()
        }
      }
    }
  }
}
