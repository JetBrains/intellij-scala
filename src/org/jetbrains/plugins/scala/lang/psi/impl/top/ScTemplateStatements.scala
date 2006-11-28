package org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements {

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 16:32:36
 */

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

  class TemplateStatement (node : ASTNode) extends ScalaPsiElementImpl (node)

  /*case class ScTemplateStatement (node : ASTNode) extends Definition (node) {
    override def toString: String = "pattern" + " " + super.toString
  } */

  /***************** definition ***********************/

  class Definition (node : ASTNode) extends TemplateStatement (node) {
    override def toString: String = "definition"
  }

  case class ScPatternDefinition (node : ASTNode) extends Definition (node) {
    override def toString: String = "pattern" + " " + super.toString
  }

  case class ScVariableDefinition (node : ASTNode) extends Definition (node) {
    override def toString: String = "variable" + " " + super.toString
  }

  case class ScFunctionDefinition (node : ASTNode) extends Definition (node) {
    override def toString: String = "function" + " " + super.toString
  }

  /************** supplementary constructor ***************/

  case class ScSelfInvocation (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "self invocation"
  }

  case class ScConstrExpr (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "constructor expression"
  }

  case class ScSupplementaryConstructor (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "supplementary constructor"
  }


  case class ScTypeDefinition (node : ASTNode) extends Definition (node) {
    override def toString: String = "type" + " " + super.toString
  }

  /***************** declaration ***********************/

  class Declaration (node : ASTNode) extends TemplateStatement (node) {
    override def toString: String = "definition"
  }

  case class ScValueDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "value" + " " + super.toString
  }

  case class ScVariableDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "variable" + " " + super.toString
  }

  case class ScFunctionDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "function" + " " + super.toString
  }

  case class ScTypeDeclaration (node : ASTNode) extends Declaration (node) {
    override def toString: String = "type" + " " + super.toString
  }

  /****************** others *************************/

  class ScFunctionSignature (node : ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "function signature"
  }    

}