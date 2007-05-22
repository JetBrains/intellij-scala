package org.jetbrains.plugins.scala.lang.psi.impl.expressions.simpleExprs

/**
* @author ilyas
*/

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.resolve.references._

import org.jetbrains.plugins.scala.lang.psi._

case class ScPropertySelectionImpl(node: ASTNode) extends ScalaExpression (node){
  override def toString: String = "Property selection"

  override def getReference = new ScalaPropertySelectionReference(this)

}

case class ScMethodCallImpl(node: ASTNode) extends ScalaExpression (node){

  override def getReference = if (getFirstChild != null){
    getFirstChild.getReference
  } else {
    null
  }

  def getAllArguments: List[ScalaExpression] = {
    if (findChildByType(ScalaElementTypes.ARG_EXPRS) != null) {
      val thisArgs = findChildByType(ScalaElementTypes.ARG_EXPRS).asInstanceOf[ScArgumentExprsImpl].getArguments
      if (this.getParent.isInstanceOf[ScMethodCallImpl])
      {
        thisArgs ::: this.getParent.asInstanceOf[ScMethodCallImpl].getAllArguments
      } else {
        thisArgs
      }
    } else {
      Nil: List[ScalaExpression]
    }
  }

  override def toString: String = "Method call"
}

case class ScGenericCallImpl(node: ASTNode) extends ScalaExpression (node){
  override def toString: String = "Generified call"
}
