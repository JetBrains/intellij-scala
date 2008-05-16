package org.jetbrains.plugins.scala.lang.formatting
/**
* @author ilyas 
*/

import java.util.List;
import java.util.ArrayList;

import com.intellij.formatting._;
import com.intellij.psi.tree._;
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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi.codeStyle.CodeStyleSettings



object getDummyBlocks{

  def apply(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val children = node.getChildren(null)
    val subBlocks = new ArrayList[Block]
    var prevChild: ASTNode = null
    node.getPsi match {
      case _: ScInfixExpr | _: ScInfixPattern | _: ScInfixType
      if (INFIX_ELEMENTS.contains(node.getLastChildNode.getElementType)) => {
        subBlocks.addAll(getInfixBlocks(node, block))
        return subBlocks
      }
      case _: ScParameters => {
        val alignment = if (mustAlignment(node,block.getSettings))
          Alignment.createAlignment
        else null
        for (val child <- children if isCorrectBlock(child)) {
          val indent = ScalaIndentProcessor.getChildIndent(block, child)
          subBlocks.add(new ScalaBlock(block, child, alignment, indent, block.getWrap, block.getSettings))
          prevChild = child
        }
        return subBlocks
      }
      case _ =>
    }
    for (val child <- children if isCorrectBlock(child)) {
      val indent = ScalaIndentProcessor.getChildIndent(block, child)
      subBlocks.add(new ScalaBlock(block, child, block.getAlignment, indent, block.getWrap, block.getSettings))
      prevChild = child
    }
    return subBlocks
  }

  private def getInfixBlocks(node: ASTNode, block: ScalaBlock): ArrayList[Block] = {
    val subBlocks = new ArrayList[Block]
    val children = node.getChildren(null)
    for (val child <- children) {
      if (INFIX_ELEMENTS.contains(child.getElementType)) {
        subBlocks.addAll(getInfixBlocks(child, block))
      } else if (isCorrectBlock(child)){
        val indent = ScalaIndentProcessor.getChildIndent(block, child)
        subBlocks.add(new ScalaBlock(block, child, block.getAlignment, indent, block.getWrap, block.getSettings))
      }
    }
    subBlocks
  }

  private def isCorrectBlock(node: ASTNode) = {
    node.getText().trim().length() > 0
  }

  private def mustAlignment(node: ASTNode, mySettings: CodeStyleSettings) = {
    node.getPsi match {
      case _: ScParameters if mySettings.ALIGN_MULTILINE_PARAMETERS => true
      case _ => false
    }
  }

  private val INFIX_ELEMENTS = TokenSet.create(Array(ScalaElementTypes.INFIX_EXPR,
      ScalaElementTypes.INFIX_PATTERN,
      ScalaElementTypes.INFIX_TYPE))

}