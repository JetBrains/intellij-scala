package org.jetbrains.plugins.scala.lang.psi.impl.primitives {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl, com.intellij.lang.ASTNode
/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 18:15:38
 */

 /*************************************************************************************/
 /********************************** PRIMITIVE TOKENS *********************************/
 /*************************************************************************************/

  abstract class ScPrimitives ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    def primitiveToString : String
    override def toString : String = {
      "PsiScalaToken: " + "'" + primitiveToString + "'"
    }
  }

  case class ScDotImpl ( node : ASTNode ) extends ScPrimitives ( node ) {
    def primitiveToString : String = "."
  }

  case class ScColonImpl ( node : ASTNode ) extends ScPrimitives ( node ) {
    def primitiveToString : String = ":"
  }

  case class ScCommaImpl ( node : ASTNode ) extends ScPrimitives ( node ) {
    def primitiveToString : String = ","
  }

  case class ScSemicolonImpl ( node : ASTNode ) extends ScPrimitives ( node ) {
    def primitiveToString : String = ";"
  }

  /*************************************************************************************/
  /*********************************** IDENTIFIER **************************************/
  /*************************************************************************************/

  case class ScIdentifierImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Identifier"
  }

  /*************************************************************************************/
  /************************************ BRACKETS ***************************************/
  /*************************************************************************************/

  case class ScLSQBracketImpl( node : ASTNode ) extends ScPrimitives (node) {
    def primitiveToString : String = "["
  }

  case class ScRSQBracketImpl( node : ASTNode ) extends ScPrimitives (node) {
    def primitiveToString : String = "]"
  }

  case class ScLBraceImpl( node : ASTNode ) extends ScPrimitives (node) {
    def primitiveToString : String = "{"
  }

  case class ScRBraceImpl( node : ASTNode ) extends ScPrimitives (node) {
    def primitiveToString : String = "}"
  }

  case class ScLParenthisImpl( node : ASTNode ) extends ScPrimitives (node) {
    def primitiveToString : String = "("
  }

  case class ScRParenthisImpl( node : ASTNode ) extends ScPrimitives (node) {
    def primitiveToString : String = ")"
  }

  /*************************************************************************************/
  /*********************************** DELIMITERS **************************************/
  /*************************************************************************************/
  case class ScStatementSeparatorImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
    override def toString : String = "Statement separator"
  }

  case class ScLineTerminatorImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
    override def toString : String = "Line terminator"
  }

  /*************************************************************************************/
  /************************************* KEYWORD ***************************************/
  /*************************************************************************************/
  case class ScCaseImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
    override def toString : String = "'case'"
  }

  case class ScModifiers ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Modifiers"
  }

  case class ScModifier ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Modifier"
  }

  case class ScAttributeClauses ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "AttributeClauses"
  }

  case class ScAttributeClause ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "AttributeClause"
  }
}