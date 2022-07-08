package org.jetbrains.plugins.scala
package codeInspection.suppression

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.{SuppressQuickFix, SuppressionUtil}
import com.intellij.psi.{PsiComment, PsiDirectory, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScCommentOwner

import java.util.regex.Matcher

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
      element.withParentsInFile
        .flatMap(commentWithSuppression)
        .headOption
    }
  }

  def commentsFor(elem: PsiElement): Seq[PsiComment] = {
    elem match {
      case null | _: PsiFile | _: PsiDirectory => Seq.empty
      case co: ScCommentOwner => co.allComments
      case stmt =>
        val prev = stmt.getPrevSiblingNotWhitespace
        prev.asOptionOf[PsiComment].toSeq
    }
  }

  def suppressActions(toolShortName: String): Array[SuppressQuickFix] = {
    val displayKey: HighlightDisplayKey = HighlightDisplayKey.find(toolShortName)
    if (displayKey != null) allFixesForKey(displayKey)
    else Array.empty
  }

  def allFixesForKey(key: HighlightDisplayKey): Array[SuppressQuickFix] = Array(
    new ScalaSuppressForStatementFix(key),
    new ScalaSuppressForClassFix(key),
    new ScalaSuppressForFunctionFix(key),
    new ScalaSuppressForVariableFix(key),
    new ScalaSuppressForTypeAliasFix(key)
  )
}
