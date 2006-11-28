package org.jetbrains.plugins.scala.lang.psi.impl.top.templates {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl;
import com.intellij.lang.ASTNode

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 17:54:19
 */

 /*************** templates **************/
  abstract class Template( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    def getTemplateParents : Parents
    override def toString: String = "template"
  }

  case class ScObjectTemplate( node : ASTNode ) extends Template ( node ) {
    override def getTemplateParents = getChild[ScTemplateParents]
    
    override def toString: String = "object" + " " + super.toString
  }

  /*********** class **************/
  case class ScClassTemplate( node : ASTNode ) extends Template ( node ) {
    override def getTemplateParents = getChild[ScTemplateParents]

    override def toString: String = "class" + " " + super.toString
  }

  case class ScRequiresBlock( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "requires block"
  }

  case class ScExtendsBlock ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "extends block"
  }

  /************ trait ***************/
  case class ScTraitTemplate( node : ASTNode ) extends Template ( node ) {
    override def getTemplateParents = getChild[ScMixinParents]

    override def toString: String = "trait" + " " + super.toString
  }

  case class ScTemplate( node : ASTNode ) extends Template ( node ) {
    override def toString: String = super.toString

    override def getTemplateParents = getChild[ScTemplateParents]
  }

  /**************** parents ****************/

  class Parents( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "parents"
  }

  case class ScTemplateParents( node : ASTNode ) extends Parents ( node ) {
    //def getConstructor = getChild[ScConstructor]

    override def toString: String = "template" + " " + super.toString
  }

  case class ScMixinParents( node : ASTNode ) extends Parents ( node ) {
  //def getType = getChild[ScConstructor]

    override def toString: String = "mixin" + " " + super.toString
  }

  /***************** body *******************/
  case class ScTemplateBody( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template body"
  }
}