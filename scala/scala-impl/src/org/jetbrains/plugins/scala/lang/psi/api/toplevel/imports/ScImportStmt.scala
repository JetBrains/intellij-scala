package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScImportStmt extends ScBlockStatement {
  def importExprs: Seq[ScImportExpr]
}