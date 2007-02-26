package org.jetbrains.plugins.scala.lang.psi.impl.literals {
/**
* @author Ilya Sergey
*/
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._

import org.jetbrains.plugins.scala.lang.psi._

  case class ScLiteralImpl( node : ASTNode ) extends ScSimpleExprImpl ( node ){
      override def toString: String = "Literal"
      def getType() : PsiType = null
  }

  case class ScUnitImpl( node : ASTNode ) extends ScExprImpl ( node ) with BlockedIndent{
      override def toString: String = "unit"
      def getType() : PsiType = null
  }
}