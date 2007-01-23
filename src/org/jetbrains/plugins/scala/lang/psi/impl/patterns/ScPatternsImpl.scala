package org.jetbrains.plugins.scala.lang.psi.impl.patterns {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

  case class ScPattern1Impl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Common pattern"
  }

  case class ScPattern3Impl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Simple pattern"
  }

  case class ScPatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Composite pattern"
  }

  case class ScPatternsImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Argument patterns"
  }

  case class ScWildPatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Wild pattern"
  }


  case class ScCaseClauseImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with IfElseIndent{
      override def toString: String = "Case Clause"
  }

}
