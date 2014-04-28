package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import base.patterns.ScCaseClauses
import com.intellij.psi.PsiElement

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScBlockExpr extends ScExpression with ScBlock {
  def caseClauses: Option[ScCaseClauses] = findChild(classOf[ScCaseClauses])
}