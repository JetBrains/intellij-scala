package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClauses extends ScalaPsiElement {
  def caseClause = findChildByClassScala(classOf[ScCaseClause])
  def caseClauses: Seq[ScCaseClause] = collection.immutable.Sequence(findChildrenByClassScala(classOf[ScCaseClause]).toSeq: _*)
}