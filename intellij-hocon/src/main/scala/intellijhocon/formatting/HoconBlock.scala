package intellijhocon
package formatting

import com.intellij.lang.ASTNode
import com.intellij.formatting._
import com.intellij.psi.formatter.common.AbstractBlock
import scala.collection.JavaConverters._
import intellijhocon.Util
import com.intellij.psi.TokenType
import intellijhocon.parser.HoconElementType
import intellijhocon.lexer.HoconTokenSets


class HoconBlock(formatter: HoconFormatter, node: ASTNode, indent: Indent, wrap: Wrap, alignment: Alignment)
  extends AbstractBlock(node, wrap, alignment) {

  import Util._

  // HoconFormatter needs these to be able to return exactly the same instances of Wrap and Alignment for
  // children of this block
  private val wrapCache = {
    val pathValueSeparatorType =
      if (node.getElementType == HoconElementType.BareObjectField)
        node.childrenIterator.map(_.getElementType).find(HoconTokenSets.PathValueSeparator.contains)
      else None
    new formatter.WrapCache(pathValueSeparatorType)
  }
  private val alignmentCache = new formatter.AlignmentCache

  override def getIndent = indent

  override def getChildAttributes(newChildIndex: Int) =
    new ChildAttributes(formatter.getChildIndent(node), formatter.getChildAlignment(alignmentCache, node))

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
      .map(createChildBlock).toVector

  private def createChildBlock(child: ASTNode) =
    new HoconBlock(formatter, child,
      formatter.getIndent(node, child),
      formatter.getWrap(wrapCache, node, child),
      formatter.getAlignment(alignmentCache, node, child))

  override def toString =
    s"${node.getElementType}[${node.getText.replaceAllLiterally("\n", "\\n")}]${node.getTextRange}" + {
      if (isLeaf) "" else children.mkString("\n", "\n", "").indent("  ")
    }
}
