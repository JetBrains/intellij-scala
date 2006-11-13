package org.jetbrains.plugins.scala.lang.psi.impl.top.defs {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 15:08:18
 */
/*************** definitions **************/
  class TmplDef( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template definition"
  }

  case class ScClassDefinition( node : ASTNode ) extends TmplDef ( node ) {
    override def toString: String = super.toString + ": " + "class"
  }

  case class ScObjectDefinition( node : ASTNode ) extends TmplDef ( node ) {
    override def toString: String = super.toString + ": " + "object"
  }

  case class ScTraitDefinition( node : ASTNode ) extends TmplDef ( node ) {
    override def toString: String = super.toString + ": " + "trait"
  }

  class ScTmplTypeParameterClause( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "type parameters clause"
  }

 /* *//*********** class **********//*
  class ScClassParameterClause( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "class parameters clause"
  }*/
}