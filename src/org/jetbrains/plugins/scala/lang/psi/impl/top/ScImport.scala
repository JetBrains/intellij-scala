package org.jetbrains.plugins.scala.lang.psi.impl.top

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.Template
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScParamClauses
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.ScTemplateStatement
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTopDefTemplate
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.icons.Icons

/**
* @author Dmitry Krasilschikov, Ilya Sergey, ven
*
*/

//  class ScImport( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
//    override def toString: String = "'import'"
//  }

class ScImportStmt(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "Import statement"

  /**
  *    Returns All import expression in current import statement
  *
  */
  def getImportExprs = childrenOfType[ScImportExpr](ScalaElementTypes.IMPORT_EXPR_BIT_SET).toList

}


/**
*  Implements all logis related to import expressions
*
*/
class ScImportExpr(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "Import expression"

  def getImportReference = getChild(ScalaElementTypes.STABLE_ID)

  private def getImportSelectors = {
    val selectorSet = getChild(ScalaElementTypes.IMPORT_SELECTORS).asInstanceOf[ScImportSelectors]
    if (selectorSet != null) selectorSet.childrenOfType[ScImportSelector](ScalaElementTypes.SELECTOR_BIT_SET).toList
    else null
  }

  def getExplicitName(name: String, prefix: String): String = {

    if (getImportReference != null){
      var refText = getImportReference.getText

      def stickNames = {
        if (refText.substring(0, 7).equals("_root_.")) {
          refText = refText.substring(7)
        } else {
          val importBegin = if (refText.contains(".")) {
            refText.substring(0, refText.indexOf("."))
          } else refText
          val index = prefix.indexOf(importBegin)
          if (index > 0 &&
          prefix.charAt(index - 1) == '.' &&
          prefix.length > index + importBegin.length &&
          prefix.charAt(index + importBegin.length) == '.'){
            refText = prefix.substring(0, index) + refText
          }
        }
      }

      if (getTailId != null && getTailId.equals(name)) {
        stickNames
        return refText + "." + name
      } else if (getImportSelectors != null) {
        stickNames
        for (val selector <- getImportSelectors) {
          if (selector.getRealName(name) != null) {
            return getImportReference.getText + "." + selector.getRealName(name)
          }
        }
        null
      } else null
    } else null
  }

  def getTailId = {
    if (getChild(ScalaTokenTypes.tIDENTIFIER) != null)
      getChild(ScalaTokenTypes.tIDENTIFIER).getText
    else null
  }

  def isExplicit = ! getText.contains("_") && ! getText.contains("{")

  def hasWildcard = getText.contains("_")

}

class ScImportSelector(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "Import selector"

  def getRealName(name: String): String = {
    if (getText.contains("_")) return null
    if (! getText.contains("=>")) {
      if (ScalaTokenTypes.tIDENTIFIER.equals(getFirstChild.getNode.getElementType) &&
      getFirstChild.getText.equals(name))
        return name
      else
        return null
    }
    val realName = getFirstChild
    val pseudoName = getLastChild
    if (ScalaTokenTypes.tIDENTIFIER.equals(realName.getNode.getElementType) &&
    ScalaTokenTypes.tIDENTIFIER.equals(pseudoName.getNode.getElementType) &&
    pseudoName.getText.equals(name)){
      realName.getText
    } else null
  }
}

class ScImportSelectors(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "Import selectors"
}

class ScImportExprs(node: ASTNode) extends ScalaPsiElementImpl (node) {
  override def toString: String = "Import expressions"
}
