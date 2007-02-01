package org.jetbrains.plugins.scala.lang.psi.impl.patterns {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

  trait ScPatterns extends PsiElement

  trait ScPattern extends ScPatterns

  trait ScPattern1 extends ScPattern

  trait ScPattern2 extends ScPattern1

  trait ScPattern3 extends ScPattern2

  case class ScTuplePatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Tuple pattern"
  }

  case class ScPattern1Impl( node : ASTNode ) extends ScalaPsiElementImpl (node) with ScPattern1 {
      override def toString: String = "Common pattern"
  }

  case class ScPattern2Impl( node : ASTNode ) extends ScalaPsiElementImpl (node) with ScPattern2 {
      override def toString: String = "Binding pattern"

      override def copy() : PsiElement = ScalaPsiElementFactory.createPattern2FromText(this.getText, this.getManager).getPsi
  }

  case class ScPattern3Impl( node : ASTNode ) extends ScalaPsiElementImpl (node) with ScPattern3 {
      override def toString: String = "Simple pattern"
  }

  case class ScPatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with ScPattern {
      override def toString: String = "Composite pattern"
  }

  case class ScPatternsImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with ScPatterns {
      override def toString: String = "Argument patterns"
  }

  case class ScWildPatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Wild pattern"
  }

  case class ScCaseClauseImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) with IfElseIndent{
      override def toString: String = "Case Clause"
  }

  case class ScCaseClausesImpl( node : ASTNode ) extends ScalaPsiElementImpl(node){
      override def toString: String = "Case Clauses"
  }

}
