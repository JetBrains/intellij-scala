package org.jetbrains.plugins.scala.lang.psi.impl.top.defs {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.Template
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScTypeParamClause
/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 15:08:18
 */
/*************** definitions **************/
  abstract class TmplDef( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template definition"

    //def getTemplateBody : Template
    def getTemplateName : String
  }

  trait TypeDef extends TmplDef (node){
    //override def getTemplateName : String = {val children = getChildren; children(1).getText}

    def hasTypeParameterClause = {}

    def typeParameterClause : ScTypeParamClause = {
      for (val child <- getChildren; child.isInstanceOf[ScTypeParamClause]) {
        return child.asInstanceOf[ScTypeParamClause]
      }
      return null
    }
  }

  trait InstanceDef extends TmplDef (node){
    def isCase : boolean = getFirstChild.getText == "case"

    //[case] class A
    override def getTemplateName : String = {val children = getChildren; if (isCase) children(2).getText else children(1).getText}
  }

  case class ScClassDefinition( node : ASTNode ) extends InstanceDef (node) with TypeDef {
    override def toString: String = super.toString + ": " + "class"
  }

  case class ScObjectDefinition( node : ASTNode ) extends InstanceDef ( node ) {
    override def toString: String = super.toString + ": " + "object"
  }

  case class ScTraitDefinition( node : ASTNode ) extends TypeDef (node){
    override def toString: String = super.toString + ": " + "trait"

    override def getTemplateName : String = {val children = getChildren; children(1).getText}
  }


 /* *//*********** class **********//*
  class ScClassParameterClause( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "class parameters clause"
  }*/
}