package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement

/**
 * NOTE: `import` and `export` are not actually "statements", but rather "clauses"
 */
trait ScImportOrExportStmt extends ScalaPsiElement {

  /**
   * NOTE: same method name for `import` and `export` statements (clauses) is used for the convenience of logic reusing<br>
   * It's done so even in Scala 3 grammar: {{{
   *   Import  ::=  `import` ImportExpr {`,` ImportExpr}
   *   Export  ::=  `export` ImportExpr {`,` ImportExpr}
   * }}}
   *
   */
  def importExprs: Seq[ScImportExpr]
}

/** Import clauses can appear anywhere */
trait ScImportStmt extends ScImportOrExportStmt with ScBlockStatement

/**
 * Export clauses can appear in classes or they can appear at the top-level.
 * An export clause cannot appear as a statement in a block.
 */
trait ScExportStmt extends ScImportOrExportStmt {
  def isTopLevel: Boolean
  def topLevelQualifier: Option[String]
}

