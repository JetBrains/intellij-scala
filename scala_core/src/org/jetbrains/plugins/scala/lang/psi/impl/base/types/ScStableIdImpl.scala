package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl




import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.base.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScStableIdImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScStableId {

  override def toString: String = "StableIdentifier"

  /*override def getReference = {
    if (! getText.contains(".") &&
    getParent != null &&
    (getParent.isInstanceOf[ScalaType] ||
    getParent.isInstanceOf[ScCompoundTypeImpl] ||
    getParent.isInstanceOf[ScConstructor] ||
    getParent.isInstanceOf[ScRequiresBlock] ||
    getParent.isInstanceOf[ScTypePatternArgsImpl] ||
    getParent.isInstanceOf[ScSimpleTypePatternImpl] ||
    getParent.isInstanceOf[ScSimpleTypePattern1Impl] ||
    getParent.isInstanceOf[ScTypePatternImpl] ||
    getParent.isInstanceOf[ScPattern1Impl] ||
    getParent.isInstanceOf[ScConstructor] ||
    getParent.isInstanceOf[ScTemplateParents] ||
    getParent.isInstanceOf[ScMixinParents])) {
      new ScalaClassReference(this)  // Class or Trait reference
    } else {
      null
      //new ScalaLocalReference(this)  // local reference
    }
  }*/

  override def getName = getText
}