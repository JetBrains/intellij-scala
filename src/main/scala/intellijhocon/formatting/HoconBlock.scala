package intellijhocon.formatting

import com.intellij.lang.ASTNode
import com.intellij.formatting._
import com.intellij.psi.formatter.common.AbstractBlock
import scala.collection.JavaConverters._
import intellijhocon.Util
import com.intellij.psi.TokenType


class HoconBlock(formatter: HoconFormatter, node: ASTNode, indent: Indent, wrap: Wrap, alignment: Alignment)
  extends AbstractBlock(node, wrap, alignment) {

  import Util._

  override def getIndent = indent

  override protected def getChildIndent =
    formatter.getChildIndent(node)

  def buildChildren() = children.asJava

  def isLeaf =
    formatter.getChildren(node).isEmpty

  def getSpacing(child1: Block, child2: Block) =
    if (child1 == null)
      formatter.getFirstSpacing(node, child2.asInstanceOf[HoconBlock].getNode)
    else
      formatter.getSpacing(node, child1.asInstanceOf[HoconBlock].getNode, child2.asInstanceOf[HoconBlock].getNode)

  lazy val children: Seq[Block] =
    formatter.getChildren(node)
      .filterNot(n => n.getTextLength == 0 || n.getElementType == TokenType.WHITE_SPACE)
      .map(child => new HoconBlock(formatter, child, formatter.getIndent(node, child), null, null))
      .toVector

  override def toString =
    s"${node.getElementType}[${node.getText.replaceAllLiterally("\n", "\\n")}]${node.getTextRange}" + {
      if (isLeaf) "" else children.mkString("\n", "\n", "").indent("  ")
    }
}
