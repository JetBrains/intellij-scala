package org.jetbrains.plugins.scala.lang.structureView

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.06.2008
*/

object StructureViewUtil {
  def getParametersAsString(x: ScParameters): String = {
    val res: StringBuffer = new StringBuffer("")
    for (child <- x.clauses) {
      res.append("(")
      res.append(getParametersAsString(child))
      res.append(")")
    }
    return res.toString()
  }
  def getParametersAsString(x: ScParameterClause): String = {
    val res = new StringBuffer("");
    for (param <- x.parameters) {
      param.paramType match {
        case Some(pt) => res.append(pt.getText()).append(", ")
        case None => res.append("AnyRef").append(", ")
      }
    }
    if (res.length >= 2)
      res.delete(res.length - 2, res.length)
    return res.toString
  }
}