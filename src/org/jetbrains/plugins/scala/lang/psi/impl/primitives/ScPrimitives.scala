package org.jetbrains.plugins.scala.lang.psi.impl.primitives {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl, com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.psi.impl.types._
  
/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 18:15:38
 */

 trait ScIdentifier extends ScStableId

  case class ScModifiers ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Modifiers"
  }

  /***************** attributes ***************/
  case class ScAttributeClauses ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Attribute clauses"
  }

  case class ScAttributeClause ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Attribute clause"
  }

  case class ScAttribute ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Attribute"
  }

   case class ScConstructor ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "constuctor"
  }
}