package org.jetbrains.plugins.scala.lang

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

import scala.annotation.tailrec

package object parser {

  implicit class PsiBuilderExt[B <: PsiBuilder](val builder: B) extends AnyVal {

    def checkedAdvanceLexer(): Unit = {
      if (!builder.eof) builder.advanceLexer()
    }

    def build(elementType: IElementType)
             (parse: B => Boolean): Boolean = {
      val marker = mark
      val result = parse(builder)

      if (result) marker.done(elementType)
      else marker.rollbackTo()

      result
    }

    def lookAhead(expected: IElementType,
                  elementTypes: IElementType*): Boolean =
      currentTokenType == expected && matchTokenTypes(elementTypes)

    def lookBack(): IElementType = lookBack(n = 1)

    private def matchTokenTypes(elementTypes: Seq[IElementType]) = elementTypes.toList match {
      case Nil => true
      case list =>
        @tailrec
        def matchTokenTypes(list: List[IElementType]): Boolean = list match {
          case Nil => true
          case head :: tail if !builder.eof && currentTokenType == head =>
            builder.advanceLexer()
            matchTokenTypes(tail)
          case _ => false
        }

        val marker = mark
        builder.advanceLexer()

        val result = matchTokenTypes(list)

        marker.rollbackTo()
        result
    }

    @tailrec
    private def lookBack(n: Int, step: Int = 1): IElementType = builder.rawLookup(-step) match {
      case whiteSpace if TokenSets.WHITESPACE_OR_COMMENT_SET.contains(whiteSpace) => lookBack(n, step + 1)
      case result if n == 0 => result
      case _ => lookBack(n - 1, step + 1)
    }

    private def mark = builder.mark

    private def currentTokenType = builder.getTokenType
  }

}
