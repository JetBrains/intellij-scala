package org.jetbrains.plugins.scala
package codeInsight.intention.expression

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.typeChecking._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScIf}

final class ReplaceTypeCheckWithMatchIntention extends PsiElementBaseIntentionAction {

  import ReplaceTypeCheckWithMatchIntention.instanceOfCall
  import TypeCheckCanBeMatchInspection.replaceTypeCheckWithMatch

  override def getFamilyName: String = ScalaBundle.message("family.name.replace.type.check.with.pattern.matching")

  override def getText: String = getFamilyName

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    instanceOfCall(element).exists { case (iioCall, _) =>
      val offset = editor.getCaretModel.getOffset

      val range = iioCall.getTextRange
      offset >= range.getStartOffset && offset <= range.getEndOffset
    }

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit =
    instanceOfCall(element).foreach { case (iioCall, ifStmt) =>
      replaceTypeCheckWithMatch(iioCall, ifStmt, onlyFirst = false)(project)
    }
}

object ReplaceTypeCheckWithMatchIntention {

  import TypeCheckCanBeMatchInspection.findIsInstanceOfCalls

  private def instanceOfCall(element: PsiElement): Option[(ScGenericCall, ScIf)] =
    for {
      IsInstanceOfCall(iioCall) <- element.parentOfType(classOf[ScGenericCall], strict = false)
      ifStmt <- iioCall.parentOfType(classOf[ScIf])
      condition <- ifStmt.condition
      if findIsInstanceOfCalls(condition).contains(iioCall)
    } yield (iioCall, ifStmt)
}
