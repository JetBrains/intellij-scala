package org.jetbrains.plugins.scala
package lang.completion

import com.intellij.codeInsight.lookup.CharFilter.Result
import com.intellij.codeInsight.lookup.{CharFilter, Lookup}
import org.jetbrains.plugins.scala.extensions.Parent
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

class ScalaCharFilter extends CharFilter {
  override def acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result = {
    if (lookup == null || lookup.getPsiElement == null) return null
    val file = lookup.getPsiFile
    if (!file.isInstanceOf[ScalaFile]) return null
    if (c == '[' || c == '{' || c == ')' || c == ']' || c == '}') {
      lookup.getPsiElement match {
        case Parent(_: ScImportExpr) =>
          // import a.<caret/>
          return Result.HIDE_LOOKUP
        case _ =>
          return Result.SELECT_ITEM_AND_FINISH_LOOKUP
      }
    }
    if (c == '`') return Result.ADD_TO_PREFIX
    if (c == ':') return Result.HIDE_LOOKUP
    null
  }
}
