package org.jetbrains.plugins.scala
package lang
package formatting

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIf
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.10.2008
 *
 * @todo consider unifying with [[org.jetbrains.plugins.scala.util.IndentUtil]]
 */
object FormatterUtil {

  def calcIndent(node: ASTNode): Int =
    node.getTreeParent.getPsi match {
      case ifStmt: ScIf =>
        ifStmt.getParent match {
          case parent: ScIf if parent.getLastChild == ifStmt && parent.elseExpression.isDefined =>
            calcIndent(node.getTreeParent)
          case parent =>
            calcAbsolutePosition(node) - calcAbsolutePosition(parent.getNode) match {
              case i if i >= 0 => i + calcIndent(parent.getNode)
              case _ => calcIndent(parent.getNode)
            }
        }
      case _: ScalaFile => 0
      case _ => calcIndent(node.getTreeParent)
    }

  def calcAbsolutePosition(node: ASTNode): Int = {
    val text = node.getPsi.getContainingFile.charSequence
    var offset = node.getTextRange.getStartOffset - 1
    var result = 0
    while (offset >= 0 && text.charAt(offset) != '\n') {
      offset += -1
      result += 1
    }
    result
  }

  def getNormalIndentString(project: Project): String = {
    val settings  = ScalaCodeStyleSettings.getInstance(project).getContainer
    val indentSize = settings.getIndentSize(ScalaFileType.INSTANCE)
    String.format("%1$" + indentSize + "s", " ")
  }

  def isCommentGrabbingPsi(element: PsiElement): Boolean = element match {
    case _: ScValue | _: ScVariable | _: ScFunction | _: ScTypeDefinition | _: ScTypeAlias => true
    case _ => false
  }

  def isDocWhiteSpace(element: PsiElement): Boolean = isDocWhiteSpace(element.getNode)
  def isDocWhiteSpace(node: ASTNode): Boolean = node.getElementType == ScalaDocTokenType.DOC_WHITESPACE
}