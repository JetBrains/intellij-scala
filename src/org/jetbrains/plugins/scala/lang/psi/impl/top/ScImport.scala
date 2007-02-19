package org.jetbrains.plugins.scala.lang.psi.impl.top {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl, com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes 

/**
 * @author Dmitry Krasilschikov, Ilya Sergey, ven
 *
 */

//  class ScImport( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
//    override def toString: String = "'import'"
//  }

  class ScImportStmt( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Import statement"
    
    def getExpression = getChild(ScalaElementTypes.IMPORT_EXPR).asInstanceOf[ScImportExpr]

    /**
    * @returns All import expression in current statement
    */
//    def getExpressions = childrenOfType[ScImportExpr](ScalaElementTypes.IMPORT_EXPR)

  }


  /**
  *  Implements all logis related to import expressions
  *
  */
  class ScImportExpr( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "Import expression"

    def getImportReference = getChild(ScalaElementTypes.STABLE_ID)
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