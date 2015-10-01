package org.jetbrains.plugins.hocon.editor

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.hocon.lexer.HoconTokenType

class HoconCommenter extends CodeDocumentationAwareCommenter {
  def getLineCommentPrefix = "//"

  def getLineCommentTokenType: IElementType = HoconTokenType.DoubleSlashComment

  def getBlockCommentSuffix = null

  def getBlockCommentPrefix = null

  def getCommentedBlockCommentPrefix = null

  def getCommentedBlockCommentSuffix = null

  def getDocumentationCommentLinePrefix: String = null

  def getBlockCommentTokenType: IElementType = null

  def getDocumentationCommentTokenType: IElementType = null

  def isDocumentationComment(element: PsiComment): Boolean = false

  def getDocumentationCommentSuffix: String = null

  def getDocumentationCommentPrefix: String = null
}
