package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.lang.ASTNode
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType

private object ScalaDocBlockBuilderUtils {

  // TODO: rename FormatterUtil to ScalaFormatterUtil
  // NOTE: maybe com.intellij.psi.impl.source.tree.LazyParseableElement ?
  // ATTENTION: current implementation will traverse the whole node together with all it's children

  /** Originally copied from private method [[com.intellij.psi.formatter.java.SimpleJavaBlock.isNotEmptyNode]] */
  def isNotEmptyNode(node: ASTNode): Boolean =
    !com.intellij.psi.formatter.FormatterUtil.containsWhiteSpacesOnly(node) &&
      node.getTextLength > 0

  def isNotEmptyDocNode(node: ASTNode): Boolean =
    !isEmptyDocNode(node)

  private def isEmptyDocNode(node: ASTNode): Boolean =
    node.getElementType match {
      case ScalaDocTokenType.DOC_WHITESPACE => true
      case ScalaDocTokenType.DOC_COMMENT_DATA |
           ScalaDocTokenType.DOC_INNER_CODE => StringUtils.isBlank(node.getText)
      case _ => node.getTextLength == 0
    }
}
