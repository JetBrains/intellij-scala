package org.jetbrains.plugins.scala.lang.parser.parsing.base

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
* @author Alexander Podkhalyuzin
* Date: 11.02.2008
*/

/*
 *  ImportSelectors ::=  {  {ImportSelector  , } (ImportSelector |  _ )  }
 */


object ImportSelectors extends ParserNode {
  def parse(builder: PsiBuilder): Boolean = {
    val importSelectorMarker = builder.mark
    //Look for {
    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => builder.advanceLexer //Ate {
      case _ => {
        builder error ErrMsg("lbrace.expected")
        importSelectorMarker.drop
        return false
      }
    }
    //Let's parse Import selectors while we will not see Import selector or will see '}'
    while (true) {
      builder.getTokenType match {
        case ScalaTokenTypes.tRBRACE => {
          builder error ErrMsg("import.selector.expected")
          builder.advanceLexer //Ate }
          importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTORS)
          return true
        }
        case ScalaTokenTypes.tUNDER => {
          builder.advanceLexer //Ate _
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => {
              builder.advanceLexer //Ate }
              importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTORS)
              return true
            }
            case _ => {
              builder error ErrMsg("rbrace.expected")
              importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTORS)
              return true
            }
          }
        }
        case ScalaTokenTypes.tIDENTIFIER => {
          ImportSelector parse builder
          builder.getTokenType match {
            case ScalaTokenTypes.tCOMMA => {
              builder.advanceLexer //Ate ,
            }
            case ScalaTokenTypes.tRBRACE => {
              builder.advanceLexer //Ate}
              importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTORS)
              return true
            }
            case _ => {
              builder error ErrMsg("rbrace.expected")
              importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTORS)
              return true
            }
          }
        }
        case _ => {
          builder error ErrMsg("rbrace.expected")
          importSelectorMarker.done(ScalaElementTypes.IMPORT_SELECTORS)
          return true
        }
      }
    }
    return true
  }
}