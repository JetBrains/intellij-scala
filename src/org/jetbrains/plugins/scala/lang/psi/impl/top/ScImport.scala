package org.jetbrains.plugins.scala.lang.psi.impl.top {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl, com.intellij.lang.ASTNode

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.10.2006
 * Time: 17:51:03
 */
  class ScImport( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "'import'"
  }

  class ScImportStmt( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Import statement"
  }

  class ScImportExpr( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Import expression"
  }

  class ScImportExprs( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Import expressions"
  }

  class ScImportSelectors( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Import selectors"
  }

  class ScImportSelector( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Import selector"
  }
};