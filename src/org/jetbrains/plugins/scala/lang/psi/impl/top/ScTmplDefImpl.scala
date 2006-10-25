package org.jetbrains.plugins.scala.lang.psi.impl.top {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 17:54:19
 */

  case class ScObject( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'object'"
  }

  case class ScTrait ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'trait'"
  }

  case class ScClass( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'class'"
  }

}