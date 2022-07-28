package org.jetbrains.plugins.scala.lang.completion.filters

import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.getLeafByOffset
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

import scala.annotation.tailrec

package object expression {

  @tailrec
  def getPrevNotWhitespaceAndComment(index: Int, context: PsiElement): Int = {
    if (index < 0) return 0
    val file = context.getContainingFile
    val fileChars = file.charSequence
    var i = index
    while (i > 0 && fileChars.charAt(i).isWhitespace) {
      i = i - 1
    }
    getLeafByOffset(i, context) match {
      case comment @ (_: PsiComment | _: ScDocComment) =>
        getPrevNotWhitespaceAndComment(comment.getTextRange.getStartOffset - 1, context)
      case _ => i
    }
  }

  @tailrec
  def getNextNotWhitespaceAndComment(index: Int, context: PsiElement): Int = {
    val file = context.getContainingFile
    if (index >= file.getTextLength - 1) return file.getTextLength - 2
    val fileChars = file.charSequence

    var i = index
    while (i < fileChars.length - 1 && fileChars.charAt(i).isWhitespace) {
      i = i + 1
    }
    getLeafByOffset(i, context) match {
      case comment @ (_: PsiComment | _: ScDocComment) =>
        getNextNotWhitespaceAndComment(comment.getTextRange.getEndOffset, context)
      case _ => i
    }
  }
}
