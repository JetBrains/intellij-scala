package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.typeChecking._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScIf, ScMatch}
import org.jetbrains.plugins.scala.lang.refactoring.util.InplaceRenameHelper


/**
 * Nikolay.Tropin
 * 5/16/13
 */
final class ReplaceTypeCheckWithMatchIntention extends PsiElementBaseIntentionAction {

  import TypeCheckCanBeMatchInspection._

  override def getFamilyName: String = ReplaceTypeCheckWithMatchIntention.FamilyName

  override def getText: String = getFamilyName

  def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    for {
      IsInstanceOfCall(iioCall) <- element.parentOfType(classOf[ScGenericCall], strict = false)
      ifStmt <- iioCall.parentOfType(classOf[ScIf])
      condition <- ifStmt.condition
      if findIsInstanceOfCalls(condition).contains(iioCall)
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
      ifStmt <- iioCall.parentOfType(classOf[ScIf])
      condition <- ifStmt.condition
      if findIsInstanceOfCalls(condition).contains(iioCall)
    } {
      val (matchStmtOption, renameData) = buildMatchStmt(ifStmt, iioCall, onlyFirst = false)(project)
      for (matchStmt <- matchStmtOption) {
        val newMatch = inWriteAction {
          ifStmt.replaceExpression(matchStmt, removeParenthesis = true).asInstanceOf[ScMatch]
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

object ReplaceTypeCheckWithMatchIntention {
  val FamilyName = "Replace type check with pattern matching"
}