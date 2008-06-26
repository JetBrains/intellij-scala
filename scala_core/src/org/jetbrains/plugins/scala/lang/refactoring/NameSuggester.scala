package org.jetbrains.plugins.scala.lang.refactoring

import java.util.regex.Matcher
import java.util.regex.Pattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScSuperReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScThisReference
import _root_.scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
* User: Alexander.Podkhalyuz
* Date: 26.06.2008
*/

object NameSuggester {
  def suggestNames(expr: ScExpression, validator: NameValidator): Array[String] = {
    val names = new ArrayBuffer[String]()
    //todo: implement suggester by type
    generateNamesByExpr(expr, names, validator)
    if (names.size == 0) {
      names += validator.validateName("value", true)
    }
    return names.toArray
  }

  private def generateNamesByExpr(expr: ScExpression, names: ArrayBuffer[String], validator: NameValidator) {
    expr match {
      case _: ScThisReference => validator.validateName("thisInstance", true)
      case _: ScSuperReference => validator.validateName("superInstance", true)
      case x: ScReferenceElement if x.refName != null => {
        val name = x.refName
        if (name != null && name.toUpperCase == name) {
          names += validator.validateName(name.toLowerCase, true)
        } else {
          generateCamelNames(names, validator, name)
        }
      }
      case x: ScMethodCall => {
        generateNamesByExpr(x.getInvokedExpr, names, validator)
      }
      case _ =>
    }
  }

  private def generateCamelNames(names: ArrayBuffer[String], validator: NameValidator, name: String) {
    if (name == "") return
    val s = deleteNonLetterFromString(name)
    for (i <- 0 to s.length-1) {
      if (i == 0) {
        val candidate = s.substring(0,1).toLowerCase + s.substring(1)
        names += validator.validateName(candidate, true)
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i,i+1).toLowerCase + s.substring(i+1)
        names += validator.validateName(candidate, true)
      }
    }
  }

  private def deleteNonLetterFromString(s: String): String = {
    val pattern: Pattern = Pattern.compile("[^a-zA-Z]");
    val matcher: Matcher = pattern.matcher(s);
    return matcher.replaceAll("");
  }
}