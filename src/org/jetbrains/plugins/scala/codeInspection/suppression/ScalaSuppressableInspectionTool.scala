package org.jetbrains.plugins.scala
package codeInspection.suppression

import java.util.regex.Matcher

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.{SuppressionUtil, SuppressQuickFix}
import com.intellij.openapi.application.{ApplicationManager, AccessToken}
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.{GrDocCommentOwner, GrDocComment}
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ObjectExt, Both}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScCommentOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

/**
 * @author Nikolay.Tropin
 */

object ScalaSuppressableInspectionTool {
  def findElementToolSuppressedIn(element: PsiElement, toolId: String): Option[PsiElement] = {
    if (element == null) return None

    def commentWithSuppression(elem: PsiElement): Option[PsiComment] = {
      for (comment <- commentsFor(elem)) {
        val text: String = comment.getText
        val matcher: Matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text)
        if (matcher.matches && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)) {
          return Some(comment)
        }
      }
      None
    }

    extensions.inReadAction {
      val iterator = (Iterator(element) ++ element.parentsInFile).flatMap(commentWithSuppression)
      if (iterator.hasNext) Some(iterator.next())
      else None
    }
  }

  def commentsFor(elem: PsiElement): Seq[PsiComment] = {
    elem match {
      case null => Seq.empty
      case co: ScCommentOwner => co.allComments
      case stmt =>
        val prev = stmt.getPrevSiblingNotWhitespace
        prev.asOptionOf[PsiComment].toSeq
    }
  }

  def suppressActions(toolShortName: String): Array[SuppressQuickFix] = {
    val displayKey: HighlightDisplayKey = HighlightDisplayKey.find(toolShortName)
    allFixesForKey(displayKey)
  }

  def allFixesForKey(key: HighlightDisplayKey): Array[SuppressQuickFix] = Array(
    new ScalaSuppressForStatementFix(key),
    new ScalaSuppressForClassFix(key),
    new ScalaSuppressForFunctionFix(key),
    new ScalaSuppressForVariableFix(key),
    new ScalaSuppressForTypeAliasFix(key)
  )
}
