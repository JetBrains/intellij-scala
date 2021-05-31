package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

import scala.annotation.tailrec

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 *  ImportSelectors ::=  {  {ImportSelector  , } (ImportSelector |  _ )  }
 */


object ImportSelectors extends ParsingRule {
  override def apply()(implicit builder: ScalaPsiBuilder): Boolean = {
    val importSelectorMarkers = builder.mark
    //Look for {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines()
      case _ =>
        builder error ErrMsg("lbrace.expected")
        importSelectorMarkers.drop()
        return false
    }
    
    def doneImportSelectors(): Unit = {
      builder.advanceLexer()
      builder.restoreNewlinesState()
      importSelectorMarkers.done(ScalaElementType.IMPORT_SELECTORS)
    }

    //Let's parse Import selectors while we will not see Import selector or will see '}'
    @tailrec
    def parseNext(expectComma: Boolean): Boolean = {
      // first process commas
      if (expectComma) {
        if (builder.consumeTrailingComma(ScalaTokenTypes.tRBRACE)) {
          doneImportSelectors()
          return true
        } else {
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA =>
              builder.advanceLexer() //Ate ,
            case ScalaTokenTypes.tRBRACE =>
              doneImportSelectors()
              return true
            case null =>
              builder.restoreNewlinesState()
              importSelectorMarkers.done(ScalaElementType.IMPORT_SELECTORS)
              return true
            case _ =>
              builder error ErrMsg("rbrace.expected")
              builder.advanceLexer()
          }
        }
      }

      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE =>
          builder error ErrMsg("import.selector.expected")
          doneImportSelectors()
          true
        case ScalaTokenTypes.tUNDER if !builder.isScala3 =>
          val importSelectorMarker = builder.mark()
          builder.advanceLexer() //Ate _
          importSelectorMarker.done(ScalaElementType.IMPORT_SELECTOR)
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE =>
              builder.advanceLexer() //Ate }
              builder.restoreNewlinesState()
              importSelectorMarkers.done(ScalaElementType.IMPORT_SELECTORS)
              true
            case _ =>
              ParserUtils.parseLoopUntilRBrace(builder, () => {}) //we need to find closing brace, otherwise we can miss important things
              builder.restoreNewlinesState()
              importSelectorMarkers.done(ScalaElementType.IMPORT_SELECTORS)
              true
          }
        case ScalaTokenTypes.tIDENTIFIER | ScalaTokenType.GivenKeyword | ScalaTokenType.WildcardStar | ScalaTokenTypes.tUNDER =>
          ImportSelector()
          parseNext(expectComma = true)

        case null =>
          builder.restoreNewlinesState()
          importSelectorMarkers.done(ScalaElementType.IMPORT_SELECTORS)
          true
        case _ =>
          builder error ErrMsg("rbrace.expected")
          builder.advanceLexer()
          parseNext(expectComma = false)
      }
    }

    parseNext(expectComma = false)
  }
}