import scala.collection.mutable._

package org.jetbrains.plugins.scala.lang.folding{

  import java.util.ArrayList;
  import com.intellij.lang.ASTNode;
  import com.intellij.lang.folding.FoldingBuilder;
  import com.intellij.lang.folding.FoldingDescriptor;
  import com.intellij.openapi.editor.Document;
  import com.intellij.psi.tree.IElementType;
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
  import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;

  /*
  * @author Ilya Sergey
  *
  */

  class ScalaFoldingBuilder extends FoldingBuilder {


    private def appendDescriptors (node: ASTNode,
                                   document: Document,
                                   descriptors: ListBuffer[FoldingDescriptor]): Unit = {

      node.getElementType match {
        case ScalaElementTypes.BLOCK_EXPR      |
             ScalaElementTypes.PACKAGING_BLOCK |
             ScalaElementTypes.TEMPLATE_BODY  => {
          descriptors += (new FoldingDescriptor(node, node.getTextRange()))
        }
        case ScalaTokenTypes.tBLOCK_COMMENT => {
          descriptors += (new FoldingDescriptor(node, node.getTextRange()))
        }
        case _ => {}
      }

      var child = node.getFirstChildNode()
      while (child != null) {
         appendDescriptors(child, document, descriptors)
         child = child.getTreeNext()
      }
    }

    def buildFoldRegions(astNode: ASTNode, document: Document) : Array[FoldingDescriptor] = {
      var descriptors = new ListBuffer[FoldingDescriptor]
      appendDescriptors(astNode, document, descriptors);
      descriptors.toList.toArray
    }

    def getPlaceholderText(node : ASTNode): String = {
      node.getElementType match {
        case ScalaElementTypes.BLOCK_EXPR      |
             ScalaElementTypes.PACKAGING_BLOCK |
             ScalaElementTypes.TEMPLATE_BODY  => {
               "{...}"
             }
        case ScalaTokenTypes.tBLOCK_COMMENT => {
          "/*...*/"
        }
        case _ => null
      }
    }

    def isCollapsedByDefault(node: ASTNode): Boolean = {
      node.getElementType == ScalaTokenTypes.tBLOCK_COMMENT
    }

  }
}