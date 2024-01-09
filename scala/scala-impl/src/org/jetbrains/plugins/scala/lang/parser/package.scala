package org.jetbrains.plugins.scala.lang

import com.intellij.lang.{PsiBuilder, WhitespacesAndCommentsBinder}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

import scala.annotation.tailrec

package object parser {

  implicit class PsiBuilderExt[B <: PsiBuilder](private val repr: B) extends AnyVal {

    def build(elementType: IElementType)
             (parse: => Boolean): Boolean = {
      val marker = repr.mark()
      val result = parse

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
    // this alters the state of the builder, so if you want to undo this with a marker,
    // the marker must be created beforehand
    def tryParseSoftKeyword(softKeyword: IElementType): Boolean = {
      if (repr.getTokenText == softKeyword.toString) {
        repr.remapCurrentToken(softKeyword)
        repr.advanceLexer()
        true
      } else false
    }

    def tryParseSoftKeywordWithRollbackMarker(softKeyword: IElementType): Option[PsiBuilder.Marker] =
      if (repr.getTokenText == softKeyword.toString) {
        val marker = new SoftKeywordRollbackMarker(repr, repr.getTokenType)
        repr.remapCurrentToken(softKeyword)
        repr.advanceLexer()
        Some(marker)
      } else None

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

    def withDisabledNewlines[T](body: => T): T = {
      repr.disableNewlines()
      try body
      finally repr.restoreNewlinesState()
    }

    def withEnabledNewlines[T](body: => T): T = {
      repr.enableNewlines()
      try body
      finally repr.restoreNewlinesState()
    }

    def withDisabledNewlinesIf[T](cond: Boolean)(body: => T): T =
      if (cond) withDisabledNewlines(body)
      else body

    def findPreviousNewLine: Option[String] = {
      val (steps, _) = repr.skipWhiteSpacesAndComments(1)

      val originalSubText = repr.getOriginalText.subSequence(
        repr.rawTokenTypeStart(1 - steps),
        repr.rawTokenTypeStart(0)
      ).toString
      if (originalSubText.contains('\n')) Some(originalSubText)
      else None
    }

    def withIndentationWidth[R](width: IndentationWidth)(body: => R): R = {
      repr.pushIndentationWidth(width)
      try body
      finally repr.popIndentationWidth()
    }

    def maybeWithIndentationWidth[R](width: Option[IndentationWidth])(body: => R): R =
      width.fold(body)(withIndentationWidth(_)(body))

    def insideBracedRegion[T](body: => T): T = {
      repr.enterBracedRegion()
      try body
      finally repr.exitBracedRegion()
    }

    def insideBracedRegionIf[T](cond: Boolean)(body: => T): T =
      if (cond) insideBracedRegion(body)
      else body

    /** Skip matching pairs of `(...)` or `[...]` parentheses.
     *
     *  The current token is `(` or `[`
     */
    def skipParensOrBrackets(multiple: Boolean = true): Unit = {
      val opening = repr.getTokenType
      assert(opening == ScalaTokenTypes.tLPARENTHESIS || opening == ScalaTokenTypes.tLSQBRACKET)
      val closing = if (opening == ScalaTokenTypes.tLPARENTHESIS) ScalaTokenTypes.tRPARENTHESIS else ScalaTokenTypes.tRSQBRACKET
      repr.advanceLexer()
      while (repr.getTokenType != null && repr.getTokenType != closing) {
        if (repr.getTokenType == opening && multiple)
          skipParensOrBrackets()
        else repr.advanceLexer()
      }
      if (repr.getTokenType != null) repr.advanceLexer()
    }
  }

  private class SoftKeywordRollbackMarker(builder: PsiBuilder, oldTokenType: IElementType) extends PsiBuilder.Marker {
    private val inner = builder.mark()

    override def precede(): PsiBuilder.Marker = inner.precede()
    override def doneBefore(`type`: IElementType, before: PsiBuilder.Marker): Unit =
      inner.doneBefore(`type`, before)
    override def doneBefore(`type`: IElementType, before: PsiBuilder.Marker, errorMessage: String): Unit =
      inner.doneBefore(`type`, before, errorMessage)
    override def errorBefore(message: String, before: PsiBuilder.Marker): Unit =
      inner.errorBefore(message, before)
    override def drop(): Unit = inner.drop()
    override def rollbackTo(): Unit = {
      inner.rollbackTo()
      builder.remapCurrentToken(oldTokenType)
    }
    override def done(`type`: IElementType): Unit =
      inner.done(`type`)
    override def collapse(`type`: IElementType): Unit =
      inner.collapse(`type`)
    override def error(message: String): Unit =
      inner.error(message)
    override def setCustomEdgeTokenBinders(left: WhitespacesAndCommentsBinder, right: WhitespacesAndCommentsBinder): Unit =
      inner.setCustomEdgeTokenBinders(left, right)
  }
}
