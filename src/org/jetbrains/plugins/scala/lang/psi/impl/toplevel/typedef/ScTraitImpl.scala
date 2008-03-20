package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 20.02.2008
* Time: 18:48:18
* To change this template use File | Settings | File Templates.
*/

class ScTraitImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTrait {

  def getExtendsBlock = getChild(ScalaElementTypes.EXTENDS_BLOCK).asInstanceOf[ScExtendsBlock]

  def getMixinParents = null /*if (getExtendsBlock != null) {
    getExtendsBlock.getMixinParents
  } else null*/

  def getMainParentName = null/*{
    if (getMixinParents != null &&
    getMixinParents.getMainConstructor != null){
      getMixinParents.getMainConstructor.getClassName
    } else {
      null
    }
  } */

  /*override def getMixinParentsNames = {
    if (getMixinParents != null){
      getMixinParents.getMixinParents.toList
    } else {
      Nil: List[ScStableId]
    }
  } */

  def setName(s: String) = this


  override def toString: String = "ScTrait"

  override def getIcon(flags: Int) = Icons.TRAIT
}
