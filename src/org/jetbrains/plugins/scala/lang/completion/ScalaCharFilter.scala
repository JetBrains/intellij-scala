package org.jetbrains.plugins.scala
package lang.completion

import com.intellij.codeInsight.lookup.{Lookup, CharFilter}
import com.intellij.codeInsight.lookup.CharFilter.Result
import lang.psi.api.ScalaFile

class ScalaCharFilter extends CharFilter {
  def acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result = {
    if (lookup == null || lookup.getPsiElement == null) return null
    val file = lookup.getPsiFile
    if (!file.isInstanceOf[ScalaFile]) return null
    if (c == '[' || c == ']') return Result.SELECT_ITEM_AND_FINISH_LOOKUP
    if (c == ':') return Result.HIDE_LOOKUP
    null
  }
}