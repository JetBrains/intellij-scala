package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import impl.statements.params._
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScParameters extends ScalaPsiElement with PsiParameterList {

  def params: Seq[ScParameter]

  def clauses: Seq[ScParameterClause]

}