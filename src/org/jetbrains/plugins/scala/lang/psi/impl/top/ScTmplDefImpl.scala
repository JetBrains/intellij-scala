package org.jetbrains.plugins.scala.lang.psi.impl.top {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 17:54:19
 */
 /*************** definitions **************/
  class ScTmplDef( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template definition"
  }

  case class ScClassDef( node : ASTNode ) extends ScTmplDef ( node ) {
    override def toString: String = super.toString + ": " + "class"
  }

  case class ScObjectDef( node : ASTNode ) extends ScTmplDef ( node ) {
    override def toString: String = super.toString + ": " + "object"
  }

  case class ScTraitDef( node : ASTNode ) extends ScTmplDef ( node ) {
    override def toString: String = super.toString + ": " + "trait"
  }


 /*************** templates **************/
  class ScTemplate( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template"
  }

  case class ScClassTemplate( node : ASTNode ) extends ScTemplate ( node ) {
    override def toString: String = "class" + " " + super.toString
  }

  case class ScTraitTemplate( node : ASTNode ) extends ScTemplate ( node ) {
    override def toString: String = "trait" + " " + super.toString
  }

  case class ScTemplateParents( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template parents"
  }

  case class ScTemplateBody( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template body"
  }
}