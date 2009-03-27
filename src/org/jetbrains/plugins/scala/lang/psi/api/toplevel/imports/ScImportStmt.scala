package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports

import com.intellij.psi.PsiElement
import packaging.ScPackaging
import expr.ScBlockStatement
import typedef.ScTypeDefinition
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScImportStmt extends ScBlockStatement {
  def importExprs = findChildrenByClass(classOf[ScImportExpr])
}