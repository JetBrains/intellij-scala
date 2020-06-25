package org.jetbrains.plugins.scala.highlighter

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.scaladoc.parser.ScalaDocElementTypes

class ScalaCommenter extends CodeDocumentationAwareCommenter  {
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
}

object ScalaCommenter extends ScalaCommenter