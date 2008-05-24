package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScImportStmt extends ScalaPsiElement {
  def importExprs = findChildrenByClass(classOf[ScImportExpr])
}