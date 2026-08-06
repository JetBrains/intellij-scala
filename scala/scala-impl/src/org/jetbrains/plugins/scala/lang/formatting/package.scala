package org.jetbrains.plugins.scala.lang

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.COMMENTS_TOKEN_SET
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr}

package object formatting {

  @inline
  private[formatting]
  def isYieldOrDo(node: ASTNode): Boolean =
    isYieldOrDo(node.getElementType)

  @inline
  private[formatting]
  def isYieldOrDo(elementType: IElementType): Boolean =
    ScalaTokenTypes.YIELD_OR_DO.contains(elementType)

  @inline
  private[formatting]
  def isComment(node: ASTNode) = COMMENTS_TOKEN_SET.contains(node.getElementType)
}
