package intellijhocon.formatting

import com.intellij.lang.ASTNode
import com.intellij.formatting.{Block, Alignment, Wrap, Indent}
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.codeStyle.CodeStyleSettings
import scala.collection.JavaConverters._
import intellijhocon.Util
import com.intellij.psi.TokenType


class HoconBlock(settings: CodeStyleSettings, node: ASTNode, indent: Indent, wrap: Wrap, alignment: Alignment)
  extends AbstractBlock(node, wrap, alignment) {

  import Util._

  override def getIndent = indent

  override protected def getChildIndent =
    FormattingManager.getChildIndent(settings, node)

  def buildChildren() = children.asJava

  def isLeaf =
    FormattingManager.getChildren(node).isEmpty

  def getSpacing(child1: Block, child2: Block) =
    FormattingManager.getSpacing(settings, child1.asInstanceOf[HoconBlock], child2.asInstanceOf[HoconBlock])

  lazy val children: Seq[Block] =
    FormattingManager.getChildren(node)
      .filterNot(n => n.getTextLength == 0 || n.getElementType == TokenType.WHITE_SPACE)
      .map(child => new HoconBlock(settings, child, FormattingManager.getIndent(settings, node, child), null, null))
      .toVector

  override def toString =
    s"${node.getElementType}[${node.getText.replaceAllLiterally("\n", "\\n")}]${node.getTextRange}" + {
      if (isLeaf) "" else children.mkString("\n", "\n", "").indent("  ")
    }
}
