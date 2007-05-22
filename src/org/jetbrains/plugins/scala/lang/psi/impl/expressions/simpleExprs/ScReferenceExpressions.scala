package org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs

/** 
* @author ilyas
*/

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

import org.jetbrains.plugins.scala.lang.psi._

case class ScReferenceExpressionImpl(node: ASTNode) extends ScalaExpression (node){

  import org.jetbrains.plugins.scala.lang.resolve.references._

  override def getReference =  new ScalaReferenceExprReference(this)

  override def getName = getText

  override def toString: String = "Reference expression"
}





case class ScThisReferenceExpressionImpl(node: ASTNode) extends ScalaExpression (node){
  override def toString: String = "This reference expression"
}

case class ScSuperReferenceExpressionImpl(node: ASTNode) extends ScalaExpression (node){
  override def toString: String = "Super reference expression"
}

