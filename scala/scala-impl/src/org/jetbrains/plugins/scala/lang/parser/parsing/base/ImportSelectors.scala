package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 *  ImportSelectors ::=  {  {ImportSelector  , } (ImportSelector |  _ )  }
 */


object ImportSelectors {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val importSelectorMarker = builder.mark
    //Look for {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE =>
        builder.advanceLexer() //Ate {
        builder.enableNewlines()
      case _ =>
        builder error ErrMsg("lbrace.expected")
        importSelectorMarker.drop()
        return false
    }
    
    def doneImportSelectors(): Unit = {
      builder.advanceLexer()
      builder.restoreNewlinesState()
      importSelectorMarker.done(ScalaElementType.IMPORT_SELECTORS)
    }
    
    //Let's parse Import selectors while we will not see Import selector or will see '}'
    while (true) {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE =>
          builder error ErrMsg("import.selector.expected")
          builder.advanceLexer() //Ate }
          builder.restoreNewlinesState()
          importSelectorMarker.done(ScalaElementType.IMPORT_SELECTORS)
          return true
        case ScalaTokenTypes.tUNDER =>
          builder.advanceLexer() //Ate _
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE =>
              builder.advanceLexer() //Ate }
              builder.restoreNewlinesState()
              importSelectorMarker.done(ScalaElementType.IMPORT_SELECTORS)
              return true
            case _ =>
              ParserUtils.parseLoopUntilRBrace(builder, () => {}) //we need to find closing brace, otherwise we can miss important things
              builder.restoreNewlinesState()
              importSelectorMarker.done(ScalaElementType.IMPORT_SELECTORS)
              return true
          }
        case ScalaTokenTypes.tIDENTIFIER =>
          ImportSelector parse builder

          if (builder.consumeTrailingComma(ScalaTokenTypes.tRBRACE)) {
            doneImportSelectors()
            return true
          }
          
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA => builder.advanceLexer() //Ate ,
            case ScalaTokenTypes.tRBRACE =>
              doneImportSelectors()
              return true
            case null =>
              builder.restoreNewlinesState()
              importSelectorMarker.done(ScalaElementType.IMPORT_SELECTORS)
              return true
            case _ =>
              builder error ErrMsg("rbrace.expected")
              builder.advanceLexer()
          }
        case null =>
          builder.restoreNewlinesState()
          importSelectorMarker.done(ScalaElementType.IMPORT_SELECTORS)
          return true
        case _ =>
          builder error ErrMsg("rbrace.expected")
          builder.advanceLexer()
      }
    }
    true
  }
}