package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import api.toplevel.ScTypeParametersOwner
import com.intellij.psi.search.LocalSearchScope
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.ScalaPsiElementImpl

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi._

import icons.Icons

import toplevel.PsiClassFake
import api.statements.params._
import com.intellij.psi.util.PsiTreeUtil
import api.base.types.ScTypeElement
import api.toplevel.typedef.ScTypeDefinition
import base.ScTypeBoundsOwnerImpl
import types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamImpl(node: ASTNode) extends ScTypeBoundsOwnerImpl(node) with ScTypeParam with PsiClassFake {
  override def toString: String = "TypeParameter"

  def getIndex() : Int = 0
  def getOwner() : PsiTypeParameterListOwner = getParent.getParent match {
    case c : PsiTypeParameterListOwner => c
    case _ => null
  }

  override def getContainingClass() = null
  
  def isCovariant = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => false
    case x => x.getText == "+"
  }

  def isContravariant = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => false
    case x => x.getText == "-"
  }

  def owner  = getParent.getParent.asInstanceOf[ScTypeParametersOwner]

  override def getUseScope  = new LocalSearchScope(owner)

  def nameId = findLastChildByType(TokenSets.ID_SET)
}