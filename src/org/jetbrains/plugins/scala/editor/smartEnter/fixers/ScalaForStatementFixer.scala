package org.jetbrains.plugins.scala
package editor.smartEnter.fixers

import com.intellij.psi._
import codeStyle.CodeStyleSettingsManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.editor.{Document, Editor}
import editor.smartEnter.ScalaSmartEnterProcessor
import impl.source.tree.JavaJspElementType
import util.PsiTreeUtil
import lang.psi.api.expr.{ScExpression, ScForStatement}
import com.intellij.openapi.project.Project
import com.intellij.lang.ASTNode

/**
 * @author Ksenia.Sautina
 * @since 1/29/13
 */

@SuppressWarnings(Array("HardCodedStringLiteral"))
class ScalaForStatementFixer extends ScalaFixer {
  def apply(editor: Editor, processor: ScalaSmartEnterProcessor, psiElement: PsiElement) {
    val forStatement = PsiTreeUtil.getParentOfType(psiElement, classOf[ScForStatement], false)
    if (forStatement == null) return
    val doc: Document = editor.getDocument
    val leftParenthesis = forStatement.getLeftParenthesis.getOrElse(null)
    val rightParenthesis = forStatement.getRightParenthesis.getOrElse(null)
    val condition = forStatement.enumerators.getOrElse(null)

    if (condition == null) {
      if (leftParenthesis == null && rightParenthesis == null) {
        val stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(forStatement.getTextRange.getStartOffset))
        doc.replaceString(forStatement.getTextRange.getStartOffset, stopOffset, "for () {\n}")
        processor.registerUnresolvedError(forStatement.getTextRange.getStartOffset + "for (".length)
      }
      else if (leftParenthesis != null && rightParenthesis == null) {
        doc.insertString(forStatement.getTextRange.getEndOffset, ") {\n}")
        processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
      } else {
        processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
      }
    } else if (rightParenthesis == null) {
      doc.insertString(condition.getTextRange.getEndOffset, ")")
    }

    /*if (leftParenthesis == null && rightParenthesis == null) {
      val stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(forStatement.getTextRange.getStartOffset))
      doc.replaceString(forStatement.getTextRange.getStartOffset, stopOffset, "for () {\n}")
      processor.registerUnresolvedError(forStatement.getTextRange.getStartOffset + "for (".length)
    }
    else if (leftParenthesis != null && rightParenthesis == null) {
      doc.insertString(forStatement.getTextRange.getEndOffset, ") {\n}")
      processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
    } else {
      processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
    } */

//    if (leftParenthesis == null || rightParenthesis == null) {
//      val textRange: TextRange = forStatement.getTextRange
//      val stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(forStatement.getTextRange.getStartOffset))
//      editor.getDocument.replaceString(textRange.getStartOffset, stopOffset, "for () {\n}")
//      processor.registerUnresolvedError(textRange.getStartOffset + "for (".length)
//      return
//    }
//    val initialization: PsiStatement = forStatement.getInitialization
//    if (initialization == null) {
//      processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
//      return
//    }
//    val condition: PsiExpression = forStatement.getCondition
//    if (condition == null) {
//      registerErrorOffset(editor, processor, initialization, forStatement)
//      return
//    }
//
//    if (forStatement.getUpdate == null) {
//      registerErrorOffset(editor, processor, condition, forStatement)
//    }
               //todo old version
    //    if (leftParenthesis == null && rightParenthesis == null) {
//      val stopOffset: Int = doc.getLineEndOffset(doc.getLineNumber(forStatement.getTextRange.getStartOffset))
//      //      stopOffset = Math.min(stopOffset, ifStatement.getTextRange.getEndOffset)
//      doc.replaceString(forStatement.getTextRange.getStartOffset, stopOffset, "for () {\n}")
//      processor.registerUnresolvedError(forStatement.getTextRange.getStartOffset + "for (".length)
//    }
//    else if (leftParenthesis != null && rightParenthesis == null) {
//      doc.insertString(forStatement.getTextRange.getEndOffset, ") {\n}")
//      processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
//    } else {
////      processor.registerUnresolvedError(leftParenthesis.getTextRange.getEndOffset)
//    }
  }

}

