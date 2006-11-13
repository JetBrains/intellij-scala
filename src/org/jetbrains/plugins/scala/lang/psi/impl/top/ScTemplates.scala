package org.jetbrains.plugins.scala.lang.psi.impl.top.templates {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 17:54:19
 */

 /*************** templates **************/
  class Template( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template"
  }

  case class ScObjectTemplate( node : ASTNode ) extends Template ( node ) {
    override def toString: String = "object" + " " + super.toString
  }

  case class ScClassTemplate( node : ASTNode ) extends Template ( node ) {
    override def toString: String = "class" + " " + super.toString
  }

  case class ScTraitTemplate( node : ASTNode ) extends Template ( node ) {
    override def toString: String = "trait" + " " + super.toString
  }

  case class ScTemplate( node : ASTNode ) extends Template ( node ) {
    override def toString: String = super.toString
  }

  /**************** parents ****************/

  class Parents( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "parents"
  }

  case class ScTemplateParents( node : ASTNode ) extends Parents ( node ) {
    override def toString: String = "template" + " " + super.toString
  }

  case class ScMixinParents( node : ASTNode ) extends Parents ( node ) {
    override def toString: String = "mixin" + " " + super.toString
  }

  /***************** body *******************/
  case class ScTemplateBody( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template body"
  }
}