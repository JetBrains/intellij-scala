package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIf

abstract class ScalaWithIfSurrounderBase extends ScalaExpressionSurrounder {
  override def getSurroundSelectionRange(editor: Editor, nodeWithIfNode: ASTNode): TextRange = {
    val stmt = unwrapParenthesis(nodeWithIfNode) match {
      case Some(stmt: ScIf) =>
        stmt.toIndentationBasedSyntax
      case _ => return nodeWithIfNode.getTextRange
    }

    getRange(editor, stmt)
  }

  protected def getRange(editor: Editor, ifStmt: ScIf): TextRange = {
    val conditionNode: ASTNode = (ifStmt.condition: @unchecked) match {
      case Some(c) => c.getNode
    }

    val offset = conditionNode.getStartOffset
    deleteText(editor, conditionNode)
    TextRange.from(offset, 0)
  }
}

abstract class ScalaWithIfConditionSurrounderBase extends ScalaWithIfSurrounderBase {
  override protected def getRange(editor: Editor, ifStmt: ScIf): TextRange = {
    val body = (ifStmt.thenExpression: @unchecked) match {
      case Some(b) => b
    }

    val offset = body.getTextRange.getStartOffset + 1
    TextRange.from(offset, 0)
  }
}
