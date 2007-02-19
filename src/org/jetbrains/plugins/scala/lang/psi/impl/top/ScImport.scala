package org.jetbrains.plugins.scala.lang.psi.impl.top {

  import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl, com.intellij.lang.ASTNode
  import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
  import org.jetbrains.plugins.scala.lang.lexer._

  /**
  * @author Dmitry Krasilschikov, Ilya Sergey, ven
  *
  */

  //  class ScImport( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
  //    override def toString: String = "'import'"
  //  }

  class ScImportStmt(node: ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "Import statement"

    /**
    *    Returns All import expression in current import statement
    *
    */
    def getImportExprs = childrenOfType[ScImportExpr](ScalaElementTypes.IMPORT_EXPR_BIT_SET).toList

  }


  /**
  *  Implements all logis related to import expressions
  *
  */
  class ScImportExpr(node: ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "Import expression"

    def getImportReference = getChild(ScalaElementTypes.STABLE_ID)

    def getImportSelectors = getChild(ScalaElementTypes.IMPORT_SELECTORS)

    def getTailId = getChild(ScalaTokenTypes.tIDENTIFIER).getText

    def isPlain = ! getText.contains("_") && ! getText.contains("{")

  }

  class ScImportExprs(node: ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "Import expressions"
  }

  class ScImportSelectors(node: ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "Import selectors"
  }

  class ScImportSelector(node: ASTNode) extends ScalaPsiElementImpl (node) {
    override def toString: String = "Import selector"
  }
};