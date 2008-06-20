package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.ScalaPsiElementImpl

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi._

import org.jetbrains.annotations._

import icons.Icons

import toplevel.PsiClassFake
import api.statements.params._
import com.intellij.psi.util.PsiTreeUtil
import api.base.types.ScTypeElement
import types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeParam with PsiClassFake {
  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def toString: String = "TypeParameter"

  def getIndex() : Int = 0
  def getOwner() : PsiTypeParameterListOwner = getParent.getParent match {
    case c : PsiClass => c
    case _ => null
  }

  def getContainingClass() = null
  def lowerBound = {
    val tLower = findChildByType(ScalaTokenTypes.tLOWER_BOUND)
    if (tLower != null) {
      PsiTreeUtil.getNextSiblingOfType(tLower, classOf[ScTypeElement]) match {
        case null => Nothing
        case te => te.getType
      }
    } else Nothing
  }

  def upperBound = {
    val tUpper = findChildByType(ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      PsiTreeUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => Any
        case te => te.getType
      }
    } else Any
  }

  def isCovariant = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case x if x != null => x.getText == "+"
    case _ => false
  }

  def isContravariant = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case x if x != null => x.getText == "-"
    case _ => false
  }
}