package org.jetbrains.plugins.scala.highlighter

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.generation.{SelfManagingCommenter, SelfManagingCommenterUtil}
import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiComment, PsiFile}
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

class ScalaCommenter extends SelfManagingCommenter[ScalaCommenterDataHolder] with CodeDocumentationAwareCommenter  {

  private val ScalaDirectivePrefix = "//>"

  override def getLineCommentPrefix = "//"

  override def getBlockCommentPrefix = "/*"
  override def getBlockCommentSuffix = "*/"

  override def getCommentedBlockCommentPrefix = "/*"
  override def getCommentedBlockCommentSuffix = "*/"

  override def getDocumentationCommentPrefix = "/**"
  override def getDocumentationCommentLinePrefix = "*"
  override def getDocumentationCommentSuffix = "*/"

  override def getLineCommentTokenType: IElementType = ScalaTokenTypes.tLINE_COMMENT
  override def getBlockCommentTokenType: IElementType = ScalaTokenTypes.tBLOCK_COMMENT
  override def getDocumentationCommentTokenType: IElementType = ScalaDocElementTypes.SCALA_DOC_COMMENT

  override def isDocumentationComment(element: PsiComment): Boolean = {
    val prefix = getDocumentationCommentPrefix
    prefix != null && {
      val text = element.getText
      text.startsWith(prefix) && !isEmptyBlockComment(text)
    }
  }

  private def isEmptyBlockComment(text: String): Boolean =
    text == "/**/" ||
      text.equals("/**\n/") // wired case when enter is pressed here /**<CARET>/

  override def createLineCommentingState(startLine: Int, endLine: Int, document: Document, file: PsiFile): ScalaCommenterDataHolder =
    new ScalaCommenterDataHolder(file)

  override def createBlockCommentingState(selectionStart: Int, selectionEnd: Int, document: Document, file: PsiFile): ScalaCommenterDataHolder =
    new ScalaCommenterDataHolder(file)

  override def commentLine(line: Int, offset: Int, document: Document, data: ScalaCommenterDataHolder): Unit = {
    val settings = CodeStyle.getLanguageSettings(data.getPsiFile)
    val prefix = getLineCommentPrefix + (if (settings.LINE_COMMENT_ADD_SPACE) " " else "")
    document.insertString(offset, prefix)
  }

  override def uncommentLine(line: Int, offset: Int, document: Document, data: ScalaCommenterDataHolder): Unit = {
    val documentText = document.getCharsSequence

    val prefix = getLineCommentPrefix
    val endOffset = offset + prefix.length

    //when LINE_COMMENT_ADD_SPACE is enabled "uncomment" should still handle both "//comment" and "// comment"
    val settings = CodeStyle.getLanguageSettings(data.getPsiFile)
    val trailingSpaceShift = if (settings.LINE_COMMENT_ADD_SPACE && documentText.charAt(endOffset) == ' ') 1 else 0

    document.deleteString(offset, endOffset + trailingSpaceShift)

    deleteSpacesIfLineIsBlank(document, documentText, line)
  }

  /**
   * Delete whitespace on line if that's all that left after uncommenting
   *
   * @note copied from [[com.intellij.codeInsight.generation.CommentByLineCommentHandler.doUncommentLine]]<br>
   *       which is not applicable for Scala language after we inherited SelfManagingCommenter
   */
  private def deleteSpacesIfLineIsBlank(document: Document, documentText: CharSequence, line: Int): Unit = {
    val lineStartOffset = document.getLineStartOffset(line)
    val lineEndOffset = document.getLineEndOffset(line)
    if (CharArrayUtil.isEmptyOrSpaces(documentText, lineStartOffset, lineEndOffset)) {
      document.deleteString(lineStartOffset, lineEndOffset)
    }
  }

  override def isLineCommented(line: Int, offset: Int, document: Document, data: ScalaCommenterDataHolder): Boolean = {
    CharArrayUtil.regionMatches(document.getCharsSequence, offset, getLineCommentPrefix) &&
      !CharArrayUtil.regionMatches(document.getCharsSequence, offset, ScalaDirectivePrefix)
  }

  override def getCommentPrefix(line: Int, document: Document, data: ScalaCommenterDataHolder): String =
    getLineCommentPrefix()

  override def getBlockCommentRange(selectionStart: Int, selectionEnd: Int, document: Document, data: ScalaCommenterDataHolder): TextRange = {
    null
  }

  override def getBlockCommentPrefix(selectionStart: Int, document: Document, data: ScalaCommenterDataHolder): String =
    getBlockCommentPrefix()

  override def getBlockCommentSuffix(selectionEnd: Int, document: Document, data: ScalaCommenterDataHolder): String =
    getBlockCommentSuffix()

  override def uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document, data: ScalaCommenterDataHolder): Unit = {
    val text = document.getCharsSequence
    def isAloneInLine(start: Int, end: Int): Boolean = {
      start == 0 || text.charAt(start - 1) == '\n' &&
        end < text.length() && text.charAt(end) == '\n'
    }

    val startIsAlone = isAloneInLine(startOffset, startOffset + getBlockCommentPrefix.length)
    val endIsAlone = isAloneInLine(endOffset - getBlockCommentSuffix.length, endOffset)

    val (prefix, suffix) =
      if (startIsAlone && endIsAlone)
        (getBlockCommentPrefix + "\n", getBlockCommentSuffix + "\n")
      else
        (getBlockCommentPrefix, getBlockCommentSuffix)

    SelfManagingCommenterUtil.uncommentBlockComment(startOffset, endOffset, document, prefix, suffix)
  }

  override def insertBlockComment(startOffset: Int, endOffset: Int, document: Document, data: ScalaCommenterDataHolder): TextRange = {
    val text = document.getCharsSequence
    def isAtStartOfLine(offset: Int): Boolean = {
      offset == 0 || text.charAt(offset - 1) == '\n'
    }

    val (prefix, suffix) =
      if (isAtStartOfLine(startOffset) && isAtStartOfLine(endOffset))
        (getBlockCommentPrefix + "\n", getBlockCommentSuffix + "\n")
      else
        (getBlockCommentPrefix, getBlockCommentSuffix)

    SelfManagingCommenterUtil.insertBlockComment(startOffset, endOffset, document, prefix, suffix)
  }
}

object ScalaCommenter extends ScalaCommenter
