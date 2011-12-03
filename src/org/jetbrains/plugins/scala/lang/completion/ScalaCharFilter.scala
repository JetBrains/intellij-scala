package org.jetbrains.plugins.scala
package lang.completion

import com.intellij.codeInsight.lookup.{Lookup, CharFilter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import lang.psi.api.expr.{ScForStatement, ScEnumerator, ScEnumerators}
import com.intellij.codeInsight.lookup.CharFilter.Result
import lang.psi.api.ScalaFile

class ScalaCharFilter extends CharFilter {
  def acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result = {
    if (lookup == null || lookup.getPsiElement == null) return null
    val file = lookup.getPsiFile
    if (!file.isInstanceOf[ScalaFile]) return null
    if (c == '[' || c == ']') return Result.SELECT_ITEM_AND_FINISH_LOOKUP
    if (c == ':') return Result.HIDE_LOOKUP
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