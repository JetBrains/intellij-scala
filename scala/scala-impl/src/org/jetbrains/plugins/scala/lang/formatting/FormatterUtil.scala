package org.jetbrains.plugins.scala
package lang
package formatting

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScIfStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 05.10.2008
 */

object FormatterUtil {
  def calcIndent(node: ASTNode): Int = {
    node.getTreeParent.getPsi match {
      case ifStmt: ScIfStmt =>
        ifStmt.getParent match {
          case parent: ScIfStmt if parent.getLastChild == ifStmt && parent.elseBranch != None => calcIndent(node.getTreeParent)
          case parent => calcAbsolutePosition(node) - calcAbsolutePosition(parent.getNode) match {
            case i if i >= 0 => i + calcIndent(parent.getNode)
            case _ => calcIndent(parent.getNode)
          }
        }
      case _: ScalaFile => 0
      case _ => calcIndent(node.getTreeParent)
    }
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
    String.format("%1$" +
      ScalaCodeStyleSettings.getInstance(project).getContainer.getIndentSize(ScalaFileType.INSTANCE) + "s", " ")
  }

  def isCommentGrabbingPsi(element: PsiElement): Boolean = element match {
    case _: ScValue | _: ScVariable | _: ScFunction | _: ScTypeDefinition | _: ScTypeAlias => true
    case _ => false
  }
}