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

class ScFunctionalTypeImpl2(node: ASTNode) extends ScalaPsiElementImpl(node) with ScalaType {
  override def toString: String = "Functional type"
}

class ScCompoundTypeImpl2(node: ASTNode) extends ScFunctionalTypeImpl2(node) with ScSimpleType2 {

  override def toString: String = "Compound type"
}

class ScInfixTypeImpl2(node: ASTNode) extends ScFunctionalTypeImpl2(node) {
  override def toString: String = "Infix type"
}

class ScRefineStatImpl2(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Refinement statement"
}

class ScRefinementsImpl2(node: ASTNode) extends ScalaPsiElementImpl(node) {
  override def toString: String = "Refinements"
}
