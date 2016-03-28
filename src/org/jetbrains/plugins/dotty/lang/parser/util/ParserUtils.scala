package org.jetbrains.plugins.dotty.lang.parser.util

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object ParserUtils {

  def parseList(builder: ScalaPsiBuilder, tokenType: IElementType, elementType: ScalaElementType, errorMessage: String)
               (parse: => Boolean): Boolean = {
    val marker = builder.mark()
    if (!parse) {
      marker.drop()
      return false
    }

    var isList = false
    while (builder.getTokenType == tokenType) {
      isList = true
      builder.advanceLexer() // ate token

      if (!parse) {
        builder.error(errorMessage)
      }
    }

    if (isList) {
      marker.done(elementType)
    } else {
      marker.drop()
    }
    true
  }

  def parseWithPrefixToken(builder: ScalaPsiBuilder, elementType: ScalaElementType, maybeErrorMessage: Option[String] = None)
                          (parse: => Boolean) = {
    val marker = builder.mark()
    builder.advanceLexer() // ate token

    if (!parse) {
      maybeErrorMessage match {
        case Some(errorMessage) => builder.error(errorMessage)
        case _ =>
      }
    }

    marker.done(elementType)
    true
  }
}
