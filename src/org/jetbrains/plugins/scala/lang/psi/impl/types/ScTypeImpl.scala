package org.jetbrains.plugins.scala.lang.psi.impl.types
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._
import org.jetbrains.plugins.scala.lang.typechecker.types._

/**
*  Main trait for all types
*/
trait ScalaType extends ScalaPsiElement {

  def getAbstractType: AbstractType = null

  def getLowerBoundType = {
    val lowerBound = getChild(ScalaElementTypes.LOWER_BOUND_TYPE)
    if (lowerBound != null) lowerBound.asInstanceOf[ScalaPsiElement].getLastChild
    else null
  }

  def getUpperBoundType = {
    val upperBound = getChild(ScalaElementTypes.UPPER_BOUND_TYPE)
    if (upperBound != null) upperBound.asInstanceOf[ScalaPsiElement].getLastChild
    else null
  }
}

class ScFunctionalTypeImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScalaType {
  override def toString: String = "Functional type"
}

class ScCompoundTypeImpl(node: ASTNode) extends ScFunctionalTypeImpl(node) with ScSimpleType {

  override def toString: String = "Compound type"
}

class ScInfixTypeImpl(node: ASTNode) extends ScFunctionalTypeImpl(node) {
  override def toString: String = "Infix type"
}

class ScRefineStatImpl(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Refinement statement"
}

class ScRefinementsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Refinements"
}
