package org.jetbrains.plugins.scala.lang.psi.impl.source

/**
* @author Dmitry Krasilschikov
*/

class ChildRole {

//todo add roles
  val NONE: Int = 0

  val EXPRESSION : Int = 1 //in class, object, trait, def, var, val

  val LPARENTH : Int = 2; // in IF_STATEMENT, FOR_STATEMENT, WHILE_STATEMENT, PARENTHESIZED_EXPRESSION
  val RPARENTH : Int = 3; // in IF_STATEMENT, FOR_STATEMENT, WHILE_STATEMENT, PARENTHESIZED_EXPRESSION

}