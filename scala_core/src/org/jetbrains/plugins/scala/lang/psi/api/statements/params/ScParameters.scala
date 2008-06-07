package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._
import com.intellij.psi._

/**
* @author Alexander Podkhalyuzin
* Date: 21.03.2008
*/

trait ScParameters extends ScalaPsiElement with PsiParameterList {

  def params: Seq[ScParameter]

  def clauses: Seq[ScParameterClause]

}