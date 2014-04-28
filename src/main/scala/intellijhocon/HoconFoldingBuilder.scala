package intellijhocon

import com.intellij.lang.folding.{FoldingDescriptor, FoldingBuilder}
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Document
import scala.collection.mutable.ArrayBuffer

class HoconFoldingBuilder extends FoldingBuilder {

  import HoconTokenType._
  import HoconElementType._

  def buildFoldRegions(node: ASTNode, document: Document): Array[FoldingDescriptor] = {
    val buffer = new ArrayBuffer[FoldingDescriptor]
    def traverse(node: ASTNode): Unit = {
      val nodeType = node.getElementType
      if (nodeType == Object || nodeType == Array || nodeType == MultilineString) {
        buffer += new FoldingDescriptor(node, node.getTextRange)
      }
      if (node.getFirstChildNode != null) {
        traverse(node.getFirstChildNode)
      }
      if (node.getTreeNext != null) {
        traverse(node.getTreeNext)
      }
    }
    traverse(node)
    buffer.toArray
  }

  def isCollapsedByDefault(node: ASTNode) =
    false

  def getPlaceholderText(node: ASTNode) = node.getElementType match {
    case Object => "{...}"
    case Array => "[...]"
    case MultilineString => "\"\"\"...\"\"\""
  }
}
