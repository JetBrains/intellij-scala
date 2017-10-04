package org.jetbrains.plugins.scala
package lang
package structureView

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScTypeExt}

/**
* @author Alexander Podkhalyuzin
* Date: 07.06.2008
*/

object StructureViewUtil {
  def getParametersAsString(x: ScParameters, short: Boolean = true,
                            subst: ScSubstitutor = ScSubstitutor.empty): String = {
    val res: StringBuffer = new StringBuffer("")
    for (child <- x.clauses) {
      res.append("(")
      res.append(getParametersAsString(child, short, subst))
      res.append(")")
    }
    res.toString
  }
  def getParametersAsString(x: ScParameterClause, short: Boolean, subst: ScSubstitutor): String = {
    val res = new StringBuffer("")
    for (param <- x.parameters) {
      if (short) {
        param.paramType match {
          case Some(pt) => res.append(pt.getText).append(", ")
          case None => res.append("AnyRef").append(", ")
        }
      } else {
        res.append(param.name + ": ")
        val typez = subst.subst(param.getType(TypingContext.empty).getOrNothing)
        res.append(typez.presentableText + (if (param.isRepeatedParameter) "*" else ""))
        res.append(", ")
      }
    }
    if (res.length >= 2)
      res.delete(res.length - 2, res.length)
    res.toString
  }
}