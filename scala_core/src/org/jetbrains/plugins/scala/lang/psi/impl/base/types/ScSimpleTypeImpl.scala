package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import tree.{IElementType, TokenSet}
import icons.Icons
import api.base.types._
import api.base.ScReferenceElement
import psi.ScalaPsiElementImpl
import lexer.ScalaTokenTypes
import scala.lang.resolve.ScalaResolveResult
import psi.types._
import api.statements.ScTypeAlias

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
      case Some(ScalaResolveResult(e, s)) => {
        if (singleton) new ScSingletonType(reference) else e match {
          case ta: ScTypeAlias => new ScTypeAliasDesignatorType(ta, s)
          case _ => new ScDesignatorType(e)
        }
      }
    }
  }
}