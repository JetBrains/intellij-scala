package org.jetbrains.plugins.scala
package lang.completion

import com.intellij.codeInsight.lookup.{Lookup, CharFilter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import lang.psi.api.expr.{ScForStatement, ScEnumerator, ScEnumerators}
import com.intellij.codeInsight.lookup.CharFilter.Result

class ScalaCharFilter extends CharFilter {
  def acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result = {
    if (lookup == null || lookup.getPsiElement == null) return null
    lookup.getPsiElement.getContext match {
      // avoids:
      //   "(va:" => "(var"
      //   "(va<SPACE>" => "(var "
      case x: ScParameterClause if Set(' ', ':').contains(c) => Result.HIDE_LOOKUP
      case _: ScEnumerators | _: ScEnumerator | _: ScForStatement if Set(' ').contains(c) =>
        Result.HIDE_LOOKUP
      case _ => null
    }
  }
}