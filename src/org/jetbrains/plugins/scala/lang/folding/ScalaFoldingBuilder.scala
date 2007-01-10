package org.jetbrains.plugins.scala.lang.folding

import _root_.scala.collection.mutable._

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
      case ScalaElementTypes.PACKAGING_BLOCK => {
        descriptors += (new FoldingDescriptor(node, node.getTextRange()))
      }
      case ScalaElementTypes.BLOCK_EXPR |
      ScalaElementTypes.INFIX_EXPR |
      ScalaElementTypes.AN_FUN |
      ScalaElementTypes.PREFIX_EXPR |
      ScalaElementTypes.POSTFIX_EXPR |
      ScalaElementTypes.SIMPLE_EXPR |
      ScalaElementTypes.IF_STMT |
      ScalaElementTypes.FOR_STMT |
      ScalaElementTypes.WHILE_STMT |
      ScalaElementTypes.DO_STMT |
      ScalaElementTypes.TRY_STMT |
      ScalaElementTypes.RETURN_STMT |
      ScalaElementTypes.METHOD_CLOSURE |
      ScalaElementTypes.THROW_STMT |
      ScalaElementTypes.ASSIGN_STMT |
      ScalaElementTypes.MATCH_STMT |
      ScalaElementTypes.TYPED_EXPR_STMT if
      (ScalaElementTypes.FUNCTION_DEFINITION.equals(node.getTreeParent().getElementType)) => {
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
      case ScalaElementTypes.PACKAGING_BLOCK => {
             "{...}"
      }
      case ScalaElementTypes.BLOCK_EXPR |
      ScalaElementTypes.INFIX_EXPR |
      ScalaElementTypes.AN_FUN |
      ScalaElementTypes.PREFIX_EXPR |
      ScalaElementTypes.POSTFIX_EXPR |
      ScalaElementTypes.SIMPLE_EXPR |
      ScalaElementTypes.IF_STMT |
      ScalaElementTypes.FOR_STMT |
      ScalaElementTypes.WHILE_STMT |
      ScalaElementTypes.DO_STMT |
      ScalaElementTypes.TRY_STMT |
      ScalaElementTypes.RETURN_STMT |
      ScalaElementTypes.METHOD_CLOSURE |
      ScalaElementTypes.THROW_STMT |
      ScalaElementTypes.ASSIGN_STMT |
      ScalaElementTypes.MATCH_STMT |
      ScalaElementTypes.TYPED_EXPR_STMT if
      (ScalaElementTypes.FUNCTION_DEFINITION.equals(node.getTreeParent().getElementType)) => {
             "{...}"
      }
      case ScalaTokenTypes.tBLOCK_COMMENT => {
        "/**...*/"
      }
      case _ => null
    }
  }

  def isCollapsedByDefault(node: ASTNode): Boolean = {
    node.getElementType == ScalaTokenTypes.tBLOCK_COMMENT
  }

}
