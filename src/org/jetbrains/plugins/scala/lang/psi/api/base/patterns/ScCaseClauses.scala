package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClauses extends ScalaPsiElement {
  def caseClause = findChildByClassScala(classOf[ScCaseClause])
  def caseClauses: Seq[ScCaseClause] = collection.immutable.Seq(findChildrenByClassScala(classOf[ScCaseClause]).toSeq: _*)
}