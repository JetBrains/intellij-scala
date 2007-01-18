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
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._;


object ScalaIndentProcessor extends ScalaTokenTypes {

  def getChildIndent(parent: ScalaBlock, child: ASTNode, prevChildNode: ASTNode) : Indent  = {

    if (parent.getNode.getPsi.isInstanceOf[ScalaFile] ||
        parent.getNode.getPsi.isInstanceOf[ScTemplateBody]) {
      return Indent.getNoneIndent()
    }

    parent.getNode.getElementType match {
      case ScalaElementTypes.BLOCK_EXPR |
           ScalaElementTypes.PACKAGING  |
           ScalaElementTypes.MATCH_STMT => {
        if (prevChildNode != null) {
          child.getElementType match {
            case ScalaTokenTypes.tRBRACE => return Indent.getNoneIndent()
            case ScalaTokenTypes.tLBRACE => return Indent.getContinuationWithoutFirstIndent()
            case _ => return Indent.getNormalIndent
          }
        }
      }
      case _ => Indent.getContinuationWithoutFirstIndent()

    }
    return Indent.getContinuationWithoutFirstIndent()
  }

}