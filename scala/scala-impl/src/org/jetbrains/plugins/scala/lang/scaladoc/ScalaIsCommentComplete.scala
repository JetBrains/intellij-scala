package org.jetbrains.plugins.scala.lang.scaladoc

import com.intellij.codeInsight.editorActions.{CommentCompleteHandler, JavaLikeQuoteHandler, QuoteHandler, TypedHandler}
import com.intellij.lang.{CodeDocumentationAwareCommenter, Language, LanguageParserDefinitions}
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.Editor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiComment, PsiElement, PsiErrorElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions.PsiFileExt

/**
  * @author Alexander Podkhalyuzin
  */

class ScalaIsCommentComplete extends CommentCompleteHandler {
  def isApplicable(comment: PsiComment, commenter: CodeDocumentationAwareCommenter): Boolean = {
    comment.getParent.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
  }

  //same code in com.intellij.codeInsight.editorActions.EnterHandler
  def isCommentComplete(comment: PsiComment, commenter: CodeDocumentationAwareCommenter, editor: Editor): Boolean = {
    val commentText: String = comment.getText
    val docComment: Boolean = isDocComment(comment, commenter)
    val expectedCommentEnd: String = if (docComment) commenter.getDocumentationCommentSuffix else commenter.getBlockCommentSuffix
    if (!commentText.endsWith(expectedCommentEnd)) return false
    val containingFile: PsiFile = comment.getContainingFile
    val language: Language = comment.getParent.getLanguage
    val lexer: Lexer = LanguageParserDefinitions.INSTANCE.forLanguage(language).createLexer(containingFile.getProject)
    val commentPrefix: String = if (docComment) commenter.getDocumentationCommentPrefix else commenter.getBlockCommentPrefix
    lexer.start(commentText, if (commentPrefix eq null) 0 else commentPrefix.length, commentText.length)
    val fileTypeHandler: QuoteHandler = TypedHandler.getQuoteHandler(containingFile, editor)
    val javaLikeQuoteHandler: JavaLikeQuoteHandler =
      fileTypeHandler match {
        case quoteHandler: JavaLikeQuoteHandler => quoteHandler
        case _ => null
      }
    while (true) {
      val tokenType: IElementType = lexer.getTokenType
      if (tokenType eq null) {
        return false
      }
      if (javaLikeQuoteHandler != null && javaLikeQuoteHandler.getStringTokenTypes != null &&
        javaLikeQuoteHandler.getStringTokenTypes.contains(tokenType)) {
        val text: String = commentText.substring(lexer.getTokenStart, lexer.getTokenEnd)
        val endOffset: Int = comment.getTextRange.getEndOffset
        if (text.endsWith(expectedCommentEnd) && endOffset < containingFile.getTextLength && containingFile.charSequence.charAt(endOffset) == '\n') {
          return true
        }
      }
      var continue = false
      if (lexer.getTokenEnd == commentText.length) {
        if (lexer.getTokenType eq commenter.getLineCommentTokenType) {
          lexer.start(commentText, lexer.getTokenStart + commenter.getLineCommentPrefix.length, commentText.length)
          lexer.advance()
          continue = true
        }
        else if (isInvalidPsi(comment)) {
          return false
        } else {
          return lexer.getTokenEnd - lexer.getTokenStart == 2 //difference from EnterHandler
        }
      }
      if (!continue && (tokenType == commenter.getDocumentationCommentTokenType ||
        tokenType == commenter.getBlockCommentTokenType)) {
        return false
      } else if (!continue) {
        lexer.advance()
      }
    }
    false
  }

  private def isDocComment(element: PsiElement, commenter: CodeDocumentationAwareCommenter): Boolean = {
    if (!element.isInstanceOf[PsiComment]) return false
    val comment: PsiComment = element.asInstanceOf[PsiComment]
    commenter.isDocumentationComment(comment)
  }

  private def isInvalidPsi(base: PsiElement): Boolean = {
    var current: PsiElement = base.getNextSibling
    while (current != null) {
      if (current.getTextLength != 0) {
        return current.isInstanceOf[PsiErrorElement]
      }
      current = current.getNextSibling
    }
    false
  }
}