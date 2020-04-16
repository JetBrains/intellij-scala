package org.jetbrains.plugins.scala
package lang

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

import scala.annotation.tailrec

package object parser {

  implicit class PsiBuilderExt[B <: PsiBuilder](private val repr: B) extends AnyVal {

    def build(elementType: IElementType)
             (parse: B => Boolean): Boolean = {
      val marker = repr.mark()
      val result = parse(repr)

      if (result) marker.done(elementType)
      else marker.rollbackTo()

      result
    }

    def predict(parse: B => Boolean): Boolean = {
      val marker = repr.mark()
      repr.advanceLexer()

      val result = parse(repr)
      marker.rollbackTo()

      result
    }

    def checkedAdvanceLexer(): Unit = if (!repr.eof) {
      repr.advanceLexer()
    }

    def lookAhead(elementTypes: IElementType*): Boolean =
      lookAhead(0, elementTypes: _*)

    def lookAhead(offset: Int, elementTypes: IElementType*): Boolean = {
      val types = elementTypes.iterator

      @tailrec
      def lookAhead(steps: Int): Boolean =
        types.isEmpty ||
          types.next() == repr.lookAhead(steps) &&
            lookAhead(steps + 1)

      lookAhead(offset)
    }

    def lookBack(expected: IElementType): Boolean = {
      val (newSteps, _) = skipWhiteSpacesAndComments(1)
      val (_, actual) = skipWhiteSpacesAndComments(newSteps + 1)
      expected == actual
    }

    @annotation.tailrec
    final def skipWhiteSpacesAndComments(steps: Int,
                                         accumulator: IElementType = null): (Int, IElementType) =
      repr.getCurrentOffset match {
        case offset if steps < offset =>
          repr.rawLookup(-steps) match {
            case whiteSpace if lexer.ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(whiteSpace) => skipWhiteSpacesAndComments(steps + 1, whiteSpace)
            case result => (steps, result)
          }
        case _ => (steps, accumulator)
      }

    def invalidVarId: Boolean = !validVarId

    // TODO: something wrong with this naming, `varid` from gammar rules is something different: `varid ::=  lower idrest`
    private def validVarId: Boolean = repr.getTokenText match {
      case "" | "`" => false
      case text => text.head.isUpper || (text.head == '`' && text.last == '`')
    }
  }

  implicit class ScalaPsiBuilderExt(private val repr: parser.parsing.builder.ScalaPsiBuilder) extends AnyVal {

    def consumeTrailingComma(expectedBrace: IElementType): Boolean = {
      val result = repr.isTrailingComma &&
        repr.predict {
          expectedBrace == _.getTokenType && findPreviousNewLine.isDefined
        }

      if (result) {
        repr.advanceLexer()
      }
      result
    }

    def findPreviousNewLine: Option[String] = {
      val (steps, _) = repr.skipWhiteSpacesAndComments(1)

      val originalSubText = repr.getOriginalText.subSequence(
        repr.rawTokenTypeStart(1 - steps),
        repr.rawTokenTypeStart(0)
      ).toString
      if (originalSubText.contains('\n')) Some(originalSubText)
      else None
    }

    def findPreviousIndent: Option[IndentationWidth] = {
      findPreviousNewLine.flatMap {
        ws =>
          val lastNewLine = ws.lastIndexOf('\n')
          if (lastNewLine < 0) None
          else IndentationWidth(ws.substring(lastNewLine + 1))
      }
    }

    def withIndentationWidth[R](width: IndentationWidth)(body: => R): R = {
      repr.pushIndentationWidth(width)
      try body
      finally repr.popIndentationWidth()
    }

    def isPrecededByNewIndent: Boolean = {
      findPreviousIndent.exists(_ > repr.currentIndentationWidth)
    }
  }
}
