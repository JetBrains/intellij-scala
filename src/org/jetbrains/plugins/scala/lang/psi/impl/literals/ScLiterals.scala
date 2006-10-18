package org.jetbrains.plugins.scala.lang.psi.impl.literals {

import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.psi.impl._

  case class ScInteger( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Integer: "+ getText
  }

  case class ScFloat( node : ASTNode ) extends ScalaPsiElementImpl(node) {
      override def toString: String = "Real: "+ getText
  }



}