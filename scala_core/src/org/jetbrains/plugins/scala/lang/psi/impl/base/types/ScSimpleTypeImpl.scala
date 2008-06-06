package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import com.intellij.lang.ASTNode
import com.intellij.psi._
import tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {

  override def toString: String = "SimpleTypeElement"

  def singleton = node.findChildByType(ScalaTokenTypes.kTYPE) != null

  override def getType() = {
    reference.bind match {
      case None => Nothing
      case Some(ScalaResolveResult(owner: PsiTypeParameterListOwner, s)) => new ScParameterizedType(owner, s)
      case Some(ScalaResolveResult(other, _)) => new ScDesignatorType(other)
    }
  }
}