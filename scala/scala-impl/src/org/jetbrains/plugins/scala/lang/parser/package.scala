package org.jetbrains.plugins.scala.lang

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

import scala.annotation.tailrec

package object parser {

  implicit class PsiBuilderExt[B <: PsiBuilder](val builder: B) extends AnyVal {

    def checkedAdvanceLexer(): Unit = {
      if (!builder.eof()) builder.advanceLexer()
    }

    def build(elementType: IElementType)
             (parse: B => Boolean): Boolean = {
      val marker = builder.mark
      val result = parse(builder)

      if (result) marker.done(elementType)
      else marker.rollbackTo()

      result
    }

    def lookBack(): IElementType = lookBack(n = 1)

    @tailrec
    private def lookBack(n: Int, step: Int = 1): IElementType = builder.rawLookup(-step) match {
      case whiteSpace if TokenSets.WHITESPACE_OR_COMMENT_SET.contains(whiteSpace) => lookBack(n, step + 1)
      case result if n == 0 => result
      case _ => lookBack(n - 1, step + 1)
    }
  }

}
