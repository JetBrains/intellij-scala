package org.jetbrains.plugins.hocon.formatting

import java.util

import com.intellij.formatting._
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock
import org.jetbrains.plugins.hocon.lexer.HoconTokenSets
import org.jetbrains.plugins.hocon.parser.HoconElementType

import scala.collection.JavaConverters._


class HoconBlock(formatter: HoconFormatter, node: ASTNode, indent: Indent, wrap: Wrap, alignment: Alignment)
  extends AbstractBlock(node, wrap, alignment) {

  import org.jetbrains.plugins.hocon.CommonUtil._

  // HoconFormatter needs these to be able to return exactly the same instances of Wrap and Alignment for
  // children of this block
  private val wrapCache = {
    val pathValueSeparatorType =
      if (node.getElementType == HoconElementType.ValuedField)
        node.childrenIterator.map(_.getElementType).find(HoconTokenSets.KeyValueSeparator.contains)
      else None
    new formatter.WrapCache(pathValueSeparatorType)
  }
  private val alignmentCache = new formatter.AlignmentCache

  override def getIndent: Indent = indent

  override def getChildAttributes(newChildIndex: Int) =
    new ChildAttributes(formatter.getChildIndent(node), formatter.getChildAlignment(alignmentCache, node))

  def buildChildren(): util.List[Block] = children.asJava

  def isLeaf: Boolean =
    formatter.getChildren(node).isEmpty

  def getSpacing(child1: Block, child2: Block): Spacing =
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

  override def toString: String =
    s"${node.getElementType}[${node.getText.replaceAllLiterally("\n", "\\n")}]${node.getTextRange}" + {
      if (isLeaf) "" else children.mkString("\n", "\n", "").indent("  ")
    }
}
