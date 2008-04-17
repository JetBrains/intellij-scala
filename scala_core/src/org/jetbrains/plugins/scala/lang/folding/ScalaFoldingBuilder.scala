package org.jetbrains.plugins.scala.lang.folding

import _root_.scala.collection.mutable._

import java.util.ArrayList;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/*
*
@author Ilya Sergey
*/

class ScalaFoldingBuilder extends FoldingBuilder {

  private def appendDescriptors(node: ASTNode,
          document: Document,
          descriptors: ListBuffer[FoldingDescriptor]): Unit = {

    node.getPsi.getChildren

    if (isMultiline(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT | ScalaElementTypes.TEMPLATE_BODY |
             ScalaTokenTypes.tDOC_COMMENT => descriptors += (new FoldingDescriptor(node, node.getTextRange()))
        case _ =>
      }
      if (node.getTreeParent() != null && ScalaElementTypes.FUNCTION_DEFINITION == node.getTreeParent().getElementType) {
        node.getPsi match {
          case _: ScBlockExpr => descriptors += new FoldingDescriptor(node, node.getTextRange())
          case _ =>
        }
      }
    }
    var child = node.getFirstChildNode()
    while (child != null) {
      appendDescriptors(child, document, descriptors)
      child = child.getTreeNext()
    }
  }

  def buildFoldRegions(astNode: ASTNode, document: Document): Array[FoldingDescriptor] = {
    var descriptors = new ListBuffer[FoldingDescriptor]
    appendDescriptors(astNode, document, descriptors);
    descriptors.toList.toArray
  }

  def getPlaceholderText(node: ASTNode): String = {
    if (isMultiline(node)) {
      node.getElementType match {
        case ScalaTokenTypes.tBLOCK_COMMENT => return "/.../"
        case ScalaTokenTypes.tDOC_COMMENT => return "/**...*/"
        case ScalaElementTypes.TEMPLATE_BODY => return "{...}"
        case _ =>
      }
    }
    if (node.getTreeParent() != null && ScalaElementTypes.FUNCTION_DEFINITION == node.getTreeParent().getElementType) {
      node.getPsi match {
        case _ : ScBlockExpr => return "{...}"
        case _ => return null
      }
    }

    return null
  }

  def isCollapsedByDefault(node: ASTNode): Boolean = {
    if (node.getTreeParent.getElementType == ScalaElementTypes.FILE &&
      node.getTreePrev == null) true
    else false
  }

  private def isMultiline(node: ASTNode): Boolean = {
     return node.getText.indexOf("\n") != -1
  }
}
