package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportExpr {
  override def toString: String = "ImportExpression"

  private def getImportSelectors = {
    val selectorSet = getChild(ScalaElementTypes.IMPORT_SELECTORS).asInstanceOf[ScImportSelectorsImpl]
    if (selectorSet != null) selectorSet.childrenOfType[ScImportSelectorImpl](TokenSets.SELECTOR_BIT_SET).toList
    else null
  }

  def getExplicitName(name: String, prefix: String, stick: Boolean): String = {
    reference match {
      case None => null
      case Some(ref) => {
        var refText = ref.getText

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
                    prefix.charAt(index + importBegin.length) == '.') {
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
              return ref.getText + "." + selector.getRealName(name)
            }
          }
          null
        } else null
      }
    }
  }

  def getTailId = null /*{
    if (getChild(ScalaElementTypes.IMPORT_END) != null) {
      getChild(ScalaElementTypes.IMPORT_END).getText
    }
    else null
  }*/

  def isExplicit = !getText.contains("_") && !getText.contains("{")

  def hasWildcard: Boolean = {
    if (getChild(ScalaTokenTypes.tUNDER) != null) return true
    val selectorSet = getChild(ScalaElementTypes.IMPORT_SELECTORS).asInstanceOf[ScImportSelectorsImpl]
    if (selectorSet != null) {
      val ss = selectorSet.childrenOfType[ScImportSelectorImpl](TokenSets.SELECTOR_BIT_SET).toList
      for (val selector <- ss) {
        if (selector.isWildcard) return true
      }
    }
    false
  }

}