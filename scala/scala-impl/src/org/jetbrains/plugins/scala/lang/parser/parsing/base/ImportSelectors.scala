package org.jetbrains.plugins.scala.lang.parser.parsing.base

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.project.ScalaXSourceFlag

import scala.annotation.tailrec

/*
 *  ImportSelectors ::=  {  {ImportSelector  , } (ImportSelector |  _ )  }
 */


object ImportSelectors extends ParsingRule {
  // under -Xsource:3, we allow `given` keyword in import selectors, but only if it also has a wildcard import
  // example:
  //   import a.{given, *}
  private val allowedTokenTextsForGivenImportUnderXSourceFlag = Set(
    "{", "*", "given", ","
  )

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val parseGivenKeywordInScala2 = builder.features.XSourceFlag != ScalaXSourceFlag.None &&
      builder.predict { builder =>
        var hadGiven = false
        var hadStar = false
        while (allowedTokenTextsForGivenImportUnderXSourceFlag.contains(builder.getTokenText)) {
          if (builder.getTokenText == "given") hadGiven = true
          if (builder.getTokenText == "*") hadStar = true
          builder.advanceLexer()
        }
        hadGiven && hadStar && builder.getTokenType == ScalaTokenTypes.tRBRACE
      }

    val importSelectorMarkers = builder.mark()
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
              ParserUtils.parseLoopUntilRBrace() {} //we need to find closing brace, otherwise we can miss important things
              builder.restoreNewlinesState()
              importSelectorMarkers.done(ScalaElementType.IMPORT_SELECTORS)
              true
          }

        case _ if parseGivenKeywordInScala2 && builder.getTokenText == "given" =>
          val importSelector = builder.mark()
          builder.remapCurrentToken(ScalaTokenType.GivenKeyword)
          builder.advanceLexer()
          importSelector.done(ScalaElementType.IMPORT_SELECTOR)
          parseNext(expectComma = true)

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