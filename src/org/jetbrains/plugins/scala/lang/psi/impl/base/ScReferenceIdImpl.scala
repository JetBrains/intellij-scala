package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.primitives._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.impl.types._

import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 22.02.2008
* Time: 10:43:21
* To change this template use File | Settings | File Templates.
*/

class ScReferenceIdImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceId{

  /*def getType: AbstractType = {
    def walkUp(elem: PsiElement): PsiElement =  {
      if (elem.isInstanceOf[ScReferenceIdContainer] || elem == null) {
        return elem
      } else {
        return walkUp(elem.getParent)
      }
    }

    val typedParent = walkUp(this)
    if (typedParent == null) {
      return null
    } else {
      if (typedParent.asInstanceOf[ScReferenceIdContainer].getExplicitType(this) != null) {
        typedParent.asInstanceOf[ScReferenceIdContainer].getExplicitType(this)
      } else {
        typedParent.asInstanceOf[ScReferenceIdContainer].getInferedType(this)
      }
    }

  }*/

  override def getName = getText

  override def toString: String = "ScScalaCodeReferenseElement"
}