package org.jetbrains.plugins.hocon.misc

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.{FoldingBuilder, FoldingDescriptor}
import com.intellij.openapi.editor.Document
import com.intellij.psi.tree.TokenSet

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class HoconFoldingBuilder extends FoldingBuilder {

  import org.jetbrains.plugins.hocon.lexer.HoconTokenType._
  import org.jetbrains.plugins.hocon.parser.HoconElementType._

  def buildFoldRegions(node: ASTNode, document: Document): Array[FoldingDescriptor] = {
    val foldableTypes = TokenSet.create(Object, Array, MultilineString)

    val buffer = ArrayBuffer[FoldingDescriptor]()
    val iterator = depthFirst(node)
    while (iterator.hasNext) {
      val n = iterator.next()
      if (foldableTypes.contains(n.getElementType) && n.getTextLength > 0) {
        buffer += new FoldingDescriptor(n, n.getTextRange)
      }
    }
    buffer.toArray
  }

  def isCollapsedByDefault(node: ASTNode) =
    false

  def getPlaceholderText(node: ASTNode): String = node.getElementType match {
    case Object => "{...}"
    case Array => "[...]"
    case MultilineString => "\"\"\"...\"\"\""
  }

  private def depthFirst(root: ASTNode): Iterator[ASTNode] = new DepthFirstIterator(root)

  private class DepthFirstIterator(node: ASTNode) extends Iterator[ASTNode] {
    private val stack = mutable.Stack[ASTNode](node)

    def hasNext: Boolean = stack.nonEmpty

    def next(): ASTNode = {
      val element = stack.pop()
      pushChildren(element)
      element
    }

    def pushChildren(element: ASTNode) {
      var child = element.getLastChildNode
      while (child != null) {
        stack.push(child)
        child = child.getTreePrev
      }
    }
  }

}
