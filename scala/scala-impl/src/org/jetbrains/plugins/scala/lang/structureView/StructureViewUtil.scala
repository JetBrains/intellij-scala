package org.jetbrains.plugins.scala
package lang
package structureView

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._

object StructureViewUtil {

  def getParametersAsString(x: ScParameters, short: Boolean = true, subst: ScSubstitutor = ScSubstitutor.empty): String = {
    val res = new StringBuilder
    renderParametersAsString(x, short, subst)(res)
    res.toString
  }

  def getParametersAsString(x: ScParameterClause, short: Boolean, subst: ScSubstitutor): String = {
    val res = new StringBuilder
    renderParametersAsString(x, short, subst)(res)
    res.toString
  }

  def renderParametersAsString(x: ScParameters, short: Boolean, subst: ScSubstitutor)(buffer: StringBuilder): Unit =
    for (child <- x.clauses) {
      buffer.append("(")
      buffer.append(getParametersAsString(child, short, subst))
      buffer.append(")")
    }

  def renderParametersAsString(x: ScParameterClause, short: Boolean, subst: ScSubstitutor)(buffer: StringBuilder): Unit = {
    var isFirst = true
    for (param <- x.parameters) {
      if (isFirst)
        isFirst = false
      else
        buffer.append(", ")
      if (short) {
        param.paramType match {
          case Some(pt) => buffer.append(pt.getText).append(", ")
          case None => buffer.append("AnyRef")
        }
      }
      else {
        buffer.append(param.name + ": ")
        val typez = subst(param.`type`().getOrNothing)
        buffer.append(typez.presentableText(x) + (if (param.isRepeatedParameter) "*" else ""))
      }
    }
  }
}