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

/*
*
@author Ilya Sergey
*
*/

class ScalaFoldingBuilder extends FoldingBuilder {

  private def appendDescriptors(node: ASTNode,
          document: Document,
          descriptors: ListBuffer[FoldingDescriptor]): Unit = {

    node.getPsi.getChildren

    node.getElementType match {
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaElementTypes.TEMPLATE_BODY |
           ScalaTokenTypes.tDOC_COMMENT =>
        descriptors += (new FoldingDescriptor(node, node.getTextRange()))
      case _ =>
    }
    if (node.getTreeParent() != null && ScalaElementTypes.FUNCTION_DEFINITION == node.getTreeParent().getElementType) {
      node.getElementType match {
        case ScalaElementTypes.FUNCTION_EXPR |
             ScalaElementTypes.IF_STMT |
             ScalaElementTypes.WHILE_STMT |
             ScalaElementTypes.TRY_STMT |
             ScalaElementTypes.DO_STMT |
             ScalaElementTypes.FOR_STMT |
             ScalaElementTypes.THROW_STMT |
             ScalaElementTypes.RETURN_STMT |
             ScalaElementTypes.ASSIGN_STMT |
             ScalaElementTypes.POSTFIX_EXPR |
             ScalaElementTypes.TYPED_EXPR_STMT |
             ScalaElementTypes.MATCH_STMT |
             ScalaElementTypes.INFIX_EXPR |
             ScalaElementTypes.PREFIX_EXPR |
             ScalaElementTypes.NEW_TEMPLATE |
             ScalaElementTypes.SIMPLE_EXPR |
             ScalaElementTypes.UNIT_EXPR |
             ScalaElementTypes.PARENT_EXPR |
             ScalaElementTypes.TUPLE |
             ScalaElementTypes.REFERENCE_EXPRESSION |
             ScalaElementTypes.PROPERTY_SELECTION |
             ScalaElementTypes.METHOD_CALL |
             ScalaElementTypes.GENERIC_CALL |
             ScalaElementTypes.BLOCK_EXPR |
             ScalaElementTypes.BLOCK =>
             descriptors += (new FoldingDescriptor(node, node.getTextRange()))
        case _ =>
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
    node.getElementType match {
      case ScalaTokenTypes.tBLOCK_COMMENT => return "/.../"
      case ScalaTokenTypes.tDOC_COMMENT => return "/**...*/"
      case ScalaElementTypes.TEMPLATE_BODY => return "{...}"
      case _ =>
    }
    if (node.getTreeParent() != null && ScalaElementTypes.FUNCTION_DEFINITION == node.getTreeParent().getElementType) {
      node.getElementType match {
        case ScalaElementTypes.FUNCTION_EXPR |
             ScalaElementTypes.IF_STMT |
             ScalaElementTypes.WHILE_STMT |
             ScalaElementTypes.TRY_STMT |
             ScalaElementTypes.DO_STMT |
             ScalaElementTypes.FOR_STMT |
             ScalaElementTypes.THROW_STMT |
             ScalaElementTypes.RETURN_STMT |
             ScalaElementTypes.ASSIGN_STMT |
             ScalaElementTypes.POSTFIX_EXPR |
             ScalaElementTypes.TYPED_EXPR_STMT |
             ScalaElementTypes.MATCH_STMT |
             ScalaElementTypes.INFIX_EXPR |
             ScalaElementTypes.PREFIX_EXPR |
             ScalaElementTypes.NEW_TEMPLATE |
             ScalaElementTypes.SIMPLE_EXPR |
             ScalaElementTypes.UNIT_EXPR |
             ScalaElementTypes.PARENT_EXPR |
             ScalaElementTypes.TUPLE |
             ScalaElementTypes.REFERENCE_EXPRESSION |
             ScalaElementTypes.PROPERTY_SELECTION |
             ScalaElementTypes.METHOD_CALL |
             ScalaElementTypes.GENERIC_CALL |
             ScalaElementTypes.BLOCK_EXPR |
             ScalaElementTypes.BLOCK =>
             return "{...}"
        case _ => return null
      }
    }
    return null
  }

  def isCollapsedByDefault(node: ASTNode): Boolean = {
    false
    //    node.getElementType == ScalaTokenTypes.tCOMMENT_CONTENT &&
    //    node.getText.substring(0, 3).equals("/**") &&
    //    (node.getText.contains('\n') || node.getText.contains('\r'))
  }

}
