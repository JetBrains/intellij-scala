package org.jetbrains.plugins.scala.lang.formatting
 
import com.intellij.formatting._;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.formatting.processors._

import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._


import java.util.List;
import java.util.ArrayList;

class ScalaBlock(private val myParentBlock: ScalaBlock,
        private val myNode: ASTNode,
        private var myAlignment: Alignment,
        private var myIndent: Indent,
        private var myWrap: Wrap,
        private val mySettings: CodeStyleSettings)
extends Object with ScalaTokenTypes with Block {

  private var mySubBlocks: List[Block] = null

  def getNode = myNode

  def getSettings = mySettings

  def getTextRange = myNode.getTextRange

  def getIndent = myIndent

  def getWrap = myWrap

  def getAlignment = myAlignment

  def isLeaf = isLeaf(myNode)

  def isIncomplete = isIncomplete(myNode)

  def getChildAttributes(newChildIndex: Int): ChildAttributes = {
    val parent = getNode.getPsi
    parent match {
      case _:ScBlockExpr | _:ScTemplateBody |
           _:ScTryBlock | _:ScCatchBlock | _:ScPackaging | _:ScMatchStmt => {
        return new ChildAttributes(Indent.getNormalIndent(), null)
      }
      case _ => new ChildAttributes(Indent.getNoneIndent(), null)
    }
  }

  def getSpacing(child1: Block, child2: Block) = {
    ScalaSpacingProcessor.getSpacing(child1.asInstanceOf[ScalaBlock], child2.asInstanceOf[ScalaBlock])
  }

  private def getDummyBlocks: ArrayList[Block] = {
    var children = myNode.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    myNode.getPsi match {
      case _: ScInfixExpr | _: ScInfixPattern | _: ScInfixType => {
        if (myNode.getLastChildNode.getElementType == ScalaElementTypes.INFIX_EXPR ||
            myNode.getLastChildNode.getElementType == ScalaElementTypes.INFIX_PATTERN ||
            myNode.getLastChildNode.getElementType == ScalaElementTypes.INFIX_TYPE) {
          def getInfixBlocks(node: ASTNode): ArrayList[Block] = {
            val subBlocks = new ArrayList[Block]
            children = node.getChildren(null)
            for (child <- children) {
              if (child.getElementType == ScalaElementTypes.INFIX_EXPR ||
                  child.getElementType == ScalaElementTypes.INFIX_PATTERN ||
                  child.getElementType == ScalaElementTypes.INFIX_TYPE) {
                subBlocks.addAll(getInfixBlocks(child))
              }
              else if (isCorrectBlock(child)){
                 val indent = ScalaIndentProcessor.getChildIndent(this, child)
                 subBlocks.add(new ScalaBlock(this,child,myAlignment,indent,myWrap,mySettings))
              }
            }
            subBlocks
          }
          subBlocks.addAll(getInfixBlocks(myNode))
          return subBlocks
        }
      }
      case _ =>
    }
    for (val child <- children) {
      if (isCorrectBlock(child)) {
        val indent = ScalaIndentProcessor.getChildIndent(this, child)
        subBlocks.add(new ScalaBlock(this, child, myAlignment, indent, myWrap, mySettings))
        prevChild = child
      }
    }
    subBlocks
  }

  def getSubBlocks: List[Block] = {
    if (mySubBlocks == null) {
      mySubBlocks = getDummyBlocks
    }
    mySubBlocks
  }

  def isLeaf(node: ASTNode) = {
    node.getFirstChildNode() == null
  }

  def isIncomplete(node: ASTNode): Boolean = {
    if (node.getPsi.isInstanceOf[PsiErrorElement])
      return true;
    var lastChild = node.getLastChildNode();
    while (lastChild != null &&
    (lastChild.getPsi.isInstanceOf[PsiWhiteSpace] || lastChild.getPsi.isInstanceOf[PsiComment])) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null){
      return false;
    }
    if (lastChild.getPsi.isInstanceOf[PsiErrorElement]) {
      return true;
    }
    return isIncomplete(lastChild);
  }

  def isCorrectBlock(node: ASTNode) = {
    node.getText().trim().length() > 0
  }

}