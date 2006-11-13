package org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements {

/**
 * User: Dmitry.Krasilschikov
 * Date: 07.11.2006
 * Time: 15:28:13
 */

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode

  abstract class ScTemplateStatement ( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    def statementName : String
    override def toString: String = "Template statement" + ": " + statementName;
  }

  case class ScValStatement ( node : ASTNode ) extends ScTemplateStatement ( node ) {
    override def statementName = "value"
  }

  case class ScVarStatement ( node : ASTNode ) extends ScTemplateStatement ( node ) {
    override def statementName = "variable"
  }

  case class ScDefStatement ( node : ASTNode ) extends ScTemplateStatement ( node ) {
    override def statementName = "definition"
  }

  case class ScDclStatement ( node : ASTNode ) extends ScTemplateStatement ( node ) {
    override def statementName = "declaration"
  }

  case class ScTmplDefStatement ( node : ASTNode ) extends ScTemplateStatement ( node ) {
    override def statementName = "template definition"
  }
}