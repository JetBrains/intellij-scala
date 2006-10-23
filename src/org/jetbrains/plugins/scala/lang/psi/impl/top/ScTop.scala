package org.jetbrains.plugins.scala.lang.psi.impl {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/**
 * User: Dmitry.Krasilschikov
 * Date: 06.10.2006
 * Time: 19:13:02
 */

  trait ClassBaseDeclaration extends TypeDeclaration

  case class ScObject( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'object'"
  }

  case class ScTrait ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'trait'"
  }

  case class ScClass( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'class'"
  }

  case class ScPackage ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'package'"
  }

  class ScImport( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'import'"
  }

}