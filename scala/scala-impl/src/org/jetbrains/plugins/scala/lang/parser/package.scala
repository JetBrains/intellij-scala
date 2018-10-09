package org.jetbrains.plugins.scala.lang

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}

import scala.annotation.tailrec

package object parser {

  implicit class PsiBuilderExt[B <: PsiBuilder](val builder: B) extends AnyVal {

    def checkedAdvanceLexer(): Unit = {
      if (notEOF) builder.advanceLexer()
    }

    def lookAhead(expected: IElementType,
                  elementTypes: IElementType*): Boolean =
      currentTokenMatches(expected) && matchTokenTypes(elementTypes)

    def lookBack(): IElementType = lookBack(n = 1)

    private def matchTokenTypes(elementTypes: Seq[IElementType]) = elementTypes.toList match {
      case Nil => true
      case list =>
        @tailrec
        def matchTokenTypes(list: List[IElementType]): Boolean = list match {
          case Nil => true
          case head :: tail if notEOF && currentTokenMatches(head) =>
            builder.advanceLexer()
            matchTokenTypes(tail)
          case _ => false
        }

        val marker = builder.mark
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

    private[parser] def currentTokenMatches(expected: IElementType) =
      builder.getTokenType == expected

    private def notEOF = !builder.eof
  }

  implicit class ScalaPsiBuilderExt(val builder: ScalaPsiBuilder) extends AnyVal {

    def build(elementType: IElementType)
             (parse: ScalaPsiBuilder => Boolean): Boolean = {
      val marker = builder.mark
      val result = parse(builder)

      if (result) marker.done(elementType)
      else marker.rollbackTo()

      result
    }

    def consumeTrailingComma(expectedBrace: IElementType): Boolean = builder.getTokenType match {
      case ScalaTokenTypes.tCOMMA if builder.isTrailingCommasEnabled &&
        nextTokenMatches(expectedBrace) =>
        builder.advanceLexer()
        true
      case _ => false
    }

    private def nextTokenMatches(expected: IElementType): Boolean = {
      val marker = builder.mark
      builder.advanceLexer()

      val result = builder.currentTokenMatches(expected) &&
        builder.asInstanceOf[ScalaPsiBuilderImpl].findPreviousNewLine.isDefined

      marker.rollbackTo()
      result
    }
  }

}
