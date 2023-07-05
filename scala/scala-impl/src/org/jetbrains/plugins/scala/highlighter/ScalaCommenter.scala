package org.jetbrains.plugins.scala.highlighter

import com.intellij.codeInsight.generation.{CommenterDataHolder, SelfManagingCommenter, SelfManagingCommenterUtil}
import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiFile}
import com.intellij.psi.tree.IElementType
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

class ScalaCommenter extends SelfManagingCommenter[CommenterDataHolder] with CodeDocumentationAwareCommenter  {
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

  override def createLineCommentingState(startLine: Int, endLine: Int, document: Document, file: PsiFile): CommenterDataHolder = {
    null
  }

  override def createBlockCommentingState(selectionStart: Int, selectionEnd: Int, document: Document, file: PsiFile): CommenterDataHolder = {
    null
  }

  override def commentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder): Unit = {
    document.insertString(offset, getLineCommentPrefix)
  }

  override def uncommentLine(line: Int, offset: Int, document: Document, data: CommenterDataHolder): Unit = {
    document.deleteString(offset, offset + getLineCommentPrefix.length)
  }

  override def isLineCommented(line: Int, offset: Int, document: Document, data: CommenterDataHolder): Boolean = {
    CharArrayUtil.regionMatches(document.getCharsSequence, offset, getLineCommentPrefix) &&
      !CharArrayUtil.regionMatches(document.getCharsSequence, offset, "//>")
  }

  override def getCommentPrefix(line: Int, document: Document, data: CommenterDataHolder): String =
    getLineCommentPrefix()

  override def getBlockCommentRange(selectionStart: Int, selectionEnd: Int, document: Document, data: CommenterDataHolder): TextRange = {
    null
  }

  override def getBlockCommentPrefix(selectionStart: Int, document: Document, data: CommenterDataHolder): String =
    getBlockCommentPrefix()

  override def getBlockCommentSuffix(selectionEnd: Int, document: Document, data: CommenterDataHolder): String =
    getBlockCommentSuffix()

  override def uncommentBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommenterDataHolder): Unit =
    SelfManagingCommenterUtil.uncommentBlockComment(startOffset, endOffset, document, getBlockCommentPrefix, getBlockCommentSuffix)

  override def insertBlockComment(startOffset: Int, endOffset: Int, document: Document, data: CommenterDataHolder): TextRange =
    SelfManagingCommenterUtil.insertBlockComment(startOffset, endOffset, document, getBlockCommentPrefix, getBlockCommentSuffix)
}

object ScalaCommenter extends ScalaCommenter
