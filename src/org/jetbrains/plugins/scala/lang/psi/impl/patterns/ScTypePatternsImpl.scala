package org.jetbrains.plugins.scala.lang.psi.impl.patterns
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

  case class ScTypePatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Type pattern"
  }

  case class ScSimpleTypePatternImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Simple type pattern"
  }

  case class ScSimpleTypePattern1Impl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Elementary type pattern"
  }

  case class ScTypePatternArgsImpl( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Type pattern arguments"
  }
