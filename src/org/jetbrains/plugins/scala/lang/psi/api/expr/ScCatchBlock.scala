package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import api.base.patterns._
import lang.parser.ScalaElementTypes
import com.intellij.psi.tree._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 18:50:19
* To change this template use File | Settings | File Templates.
*/

trait ScCatchBlock extends ScalaPsiElement {

  def caseClauses: Iterable[ScCaseClause] = {
    val caseClauses = getChild(ScalaElementTypes.CASE_CLAUSES).asInstanceOf[ScalaPsiElement]
    caseClauses.childrenOfType[ScCaseClause](TokenSet.create(Array(ScalaElementTypes.CASE_CLAUSE)))
  }

}