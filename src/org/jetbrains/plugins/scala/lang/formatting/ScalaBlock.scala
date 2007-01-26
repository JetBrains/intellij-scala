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
import org.jetbrains.plugins.scala.lang.psi.ScalaFile;
import org.jetbrains.plugins.scala.lang.formatting.processors._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

import java.util.List;
import java.util.ArrayList;

class ScalaBlock(private val myParentBlock : ScalaBlock,
                 private val myNode : ASTNode,
                 private var myAlignment : Alignment,
                 private var myIndent: Indent,
                 private var myWrap : Wrap,
                 private val mySettings : CodeStyleSettings)
  extends Object with ScalaTokenTypes with Block {

  private var mySubBlocks : List[Block] = null

  def getNode = myNode

  def getSettings = mySettings

  def getTextRange = myNode.getTextRange

  def getIndent = myIndent

  def getWrap = myWrap

  def getAlignment = myAlignment

  def isLeaf = isLeaf(myNode)

  def isIncomplete = isIncomplete(myNode)

  def getChildAttributes(newChildIndex: Int) : ChildAttributes = {
    val parent = getNode.getPsi
    if (parent.isInstanceOf[BlockedIndent] ) {
      return new ChildAttributes(Indent.getNormalIndent(), null)
    }
    new ChildAttributes(Indent.getNoneIndent(), null)
  }

  def getSpacing(child1: Block, child2: Block) = {
    ScalaSpacingProcessor.getSpacing((child1.asInstanceOf[ScalaBlock]).getNode, (child2.asInstanceOf[ScalaBlock]).getNode)
  }

  private def getDummyBlocks = {
    var children = myNode.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    for (val child <- children) {
      if (isCorrectBlock(child)) {
        val indent = ScalaIndentProcessor.getChildIndent(this, child)
        subBlocks.add(new ScalaBlock(this, child, myAlignment, indent, myWrap, mySettings))
        prevChild = child
      }
    }
    subBlocks
  }

  def getSubBlocks : List[Block] = {
    if (mySubBlocks == null) {
      mySubBlocks = getDummyBlocks
    }
    mySubBlocks
  }

  def isLeaf(node:ASTNode) = {
    node.getFirstChildNode() == null
  }

  def isIncomplete (node: ASTNode) : Boolean = {
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


  def isCorrectBlock(node:ASTNode) = {
    node.getText().trim().length()>0
  }

}