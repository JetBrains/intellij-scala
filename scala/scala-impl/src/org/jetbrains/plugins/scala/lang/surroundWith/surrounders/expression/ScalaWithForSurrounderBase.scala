package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFor

abstract class ScalaWithForSurrounderBase extends ScalaExpressionSurrounder {
  override def getSurroundSelectionRange(editor: Editor, withForNode: ASTNode): TextRange = {
    val forStmt = unwrapParenthesis(withForNode) match {
      case Some(stmt: ScFor) =>
        stmt.toIndentationBasedSyntax
      case _ => return withForNode.getTextRange
    }

    val enums = (forStmt.enumerators: @unchecked) match {
      case Some(x) => x.getNode
    }

    val offset = enums.getTextRange.getStartOffset
    deleteText(editor, enums)
    TextRange.from(offset, 0)
  }
}
