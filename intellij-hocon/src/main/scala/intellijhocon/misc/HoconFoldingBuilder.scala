package intellijhocon
package misc

import com.intellij.lang.folding.{FoldingDescriptor, FoldingBuilder}
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import com.intellij.psi.tree.TokenSet
import intellijhocon.lexer.HoconTokenType
import intellijhocon.parser.HoconElementType

class HoconFoldingBuilder extends FoldingBuilder {

  import HoconTokenType._
  import HoconElementType._

  def buildFoldRegions(node: ASTNode, document: Document): Array[FoldingDescriptor] = {
    val foldableTypes = TokenSet.create(Object, Array, MultilineString)
    def nodesIterator(root: ASTNode): Iterator[ASTNode] =
      Iterator(root) ++ Iterator.iterate(root.getFirstChildNode)(_.getTreeNext).takeWhile(_ != null).flatMap(nodesIterator)

    nodesIterator(node).collect {
      case n if foldableTypes.contains(n.getElementType) && n.getTextLength > 0 =>
        new FoldingDescriptor(n, n.getTextRange)
    }.toArray
  }

  def isCollapsedByDefault(node: ASTNode) =
    false

  def getPlaceholderText(node: ASTNode) = node.getElementType match {
    case Object => "{...}"
    case Array => "[...]"
    case MultilineString => "\"\"\"...\"\"\""
  }
}
