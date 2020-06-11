package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.lookup.{CharFilter, Lookup}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

class ScalaCharFilter extends CharFilter {

  import CharFilter.Result

  override def acceptChar(char: Char,
                          prefixLength: Int,
                          lookup: Lookup): Result =
    if (lookup != null &&
      lookup.getPsiFile.isInstanceOf[ScalaFile])
      lookup.getPsiElement match {
        case null => null
        case element => acceptChar(char, element)
      }
    else
      null

  import Result._

  private def acceptChar(char: Char,
                         element: PsiElement) = char match {
    case ')' |
         '[' | ']' |
         '{' | '}' =>
      element.getParent match {
        case _: ScImportExpr => HIDE_LOOKUP // import a.<caret/>
        case _ => SELECT_ITEM_AND_FINISH_LOOKUP
      }
    case '`' => ADD_TO_PREFIX
    case ':' => HIDE_LOOKUP
    case _ => null
  }
}
