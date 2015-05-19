package org.jetbrains.plugins.scala
package lang
package formatting

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIfStmt

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.10.2008
 */

object FormatterUtil {
  def calcIndent(node: ASTNode): Int = {
    node.getTreeParent.getPsi match {
      case ifStmt: ScIfStmt => {
        ifStmt.getParent match {
          case parent: ScIfStmt if parent.getLastChild == ifStmt && parent.elseBranch != None => calcIndent(node.getTreeParent)
          case parent => calcAbsolutePosition(node) - calcAbsolutePosition(parent.getNode) match {
            case i if i >= 0 => i + calcIndent(parent.getNode)
            case _ => calcIndent(parent.getNode)
          }
        }
      }
      case _: ScalaFile => 0
      case _ => calcIndent(node.getTreeParent)
    }
  }
  def calcAbsolutePosition(node: ASTNode): Int = {
    val text = node.getPsi.getContainingFile.getText
    var offset = node.getTextRange.getStartOffset - 1
    var result = 0
    while (offset >= 0 && text(offset) != '\n') {offset += -1; result += 1}
    result
  }

  def getNormalIndentString(project: Project) = {
    String.format("%1$" +
        ScalaCodeStyleSettings.getInstance(project).getContainer.getIndentSize(ScalaFileType.SCALA_FILE_TYPE) + "s", " ")
  }
}