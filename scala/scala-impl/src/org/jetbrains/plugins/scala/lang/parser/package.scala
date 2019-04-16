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
      elementTypes.zipWithIndex.forall {
        case (elementType, index) => elementType == repr.lookAhead(index)
      }

    def lookBack(): IElementType = lookBack(n = 1)

    @tailrec
    private def lookBack(n: Int, step: Int = 1): IElementType = repr.rawLookup(-step) match {
      case whiteSpace if TokenSets.WHITESPACE_OR_COMMENT_SET.contains(whiteSpace) => lookBack(n, step + 1)
      case result if n == 0 => result
      case _ => lookBack(n - 1, step + 1)
    }
  }

  import parser.parsing.builder.{ScalaPsiBuilder, ScalaPsiBuilderImpl}

  implicit class ScalaPsiBuilderExt(private val repr: ScalaPsiBuilder) extends AnyVal {

    def consumeTrailingComma(expectedBrace: IElementType): Boolean = {
      val result = repr.isTrailingComma &&
        repr.predict {
          case builder: ScalaPsiBuilderImpl if expectedBrace == builder.getTokenType => builder.findPreviousNewLine.isDefined
          case _ => false
        }

      if (result) {
        repr.advanceLexer()
      }
      result
    }

  }
}
