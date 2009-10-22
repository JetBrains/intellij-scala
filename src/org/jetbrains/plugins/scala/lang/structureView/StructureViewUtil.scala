package org.jetbrains.plugins.scala
package lang
package structureView

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import psi.types.result.TypingContext
import psi.types.{Nothing, ScType}

/** 
* @author Alexander Podkhalyuzin
* Date: 07.06.2008
*/

object StructureViewUtil {
  def getParametersAsString(x: ScParameters): String = getParametersAsString(x, true)
  def getParametersAsString(x: ScParameters, short: Boolean): String = {
    val res: StringBuffer = new StringBuffer("")
    for (child <- x.clauses) {
      res.append("(")
      res.append(getParametersAsString(child, short))
      res.append(")")
    }
    return res.toString()
  }
  def getParametersAsString(x: ScParameterClause, short: Boolean): String = {
    val res = new StringBuffer("");
    for (param <- x.parameters) {
      if (short) {
        param.paramType match {
          case Some(pt) => res.append(pt.getText()).append(", ")
          case None => res.append("AnyRef").append(", ")
        }
      } else {
        res.append(param.name + ": ")
        res.append(ScType.presentableText(param.getType(TypingContext.empty) getOrElse Nothing) + (if (param.isRepeatedParameter) "*" else ""))
        res.append(", ")
      }
    }
    if (res.length >= 2)
      res.delete(res.length - 2, res.length)
    return res.toString
  }
}