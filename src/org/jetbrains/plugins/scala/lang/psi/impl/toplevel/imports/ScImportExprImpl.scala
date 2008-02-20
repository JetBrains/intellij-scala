package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

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
import org.jetbrains.plugins.scala.lang.resolve.references._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 20.02.2008
* Time: 17:33:32
* To change this template use File | Settings | File Templates.
*/

class ScImportExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportExpr{
  override def toString: String = "ImportExpression"

  def getImportReference = getChild(ScalaElementTypes.STABLE_ID)

  private def getImportSelectors = {
    val selectorSet = getChild(ScalaElementTypes.IMPORT_SELECTORS).asInstanceOf[ScImportSelectorsImpl]
    if (selectorSet != null) selectorSet.childrenOfType[ScImportSelectorImpl](ScalaElementTypes.SELECTOR_BIT_SET).toList
    else null
  }

  def getExplicitName(name: String, prefix: String, stick: Boolean): String = {

    if (getImportReference != null){
      var refText = getImportReference.getText

      def stickNames = {
        if (refText.length > 7 && refText.substring(0, 7).equals("_root_.")) {
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
          } else {
            refText = prefix + refText
          }
        }
      }

      if (getTailId != null && getTailId.equals(name)) {
        if (stick) stickNames
        return refText + "." + name
      } else if (getImportSelectors != null) {
        if (stick) stickNames
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
    if (getChild(ScalaElementTypes.IMPORT_END) != null) {
      getChild(ScalaElementTypes.IMPORT_END).getText
    }
    else null
  }

  def isExplicit = ! getText.contains("_") && ! getText.contains("{")

  def hasWildcard: Boolean = {
    if (getChild(ScalaTokenTypes.tUNDER) != null) return true
    val selectorSet = getChild(ScalaElementTypes.IMPORT_SELECTORS).asInstanceOf[ScImportSelectorsImpl]
    if (selectorSet != null) {
      val ss = selectorSet.childrenOfType[ScImportSelectorImpl](ScalaElementTypes.SELECTOR_BIT_SET).toList
      for (val selector <- ss) {
        if (selector.isWildcard) return true
      }
    }
    false
  }

}